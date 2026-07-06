/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ExtensionDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/payments/final")
public class FinalPaymentController extends HttpServlet {

    private final ExtensionDAO dao = new ExtensionDAO();
    private final Gson gson = new Gson();

    /**
     * Yêu cầu là Customer đã đăng nhập (P2-2: chặn ai-cũng-gọi-được xác nhận
     * thanh toán). TODO: bổ sung kiểm ownership (customer sở hữu booking).
     */
    private boolean requireCustomer(HttpServletRequest request, HttpServletResponse response, Map<String, Object> res) throws IOException {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            res.put("success", false);
            res.put("message", "Unauthorized");
            response.getWriter().print(gson.toJson(res));
            return false;
        }
        if (!"Customer".equalsIgnoreCase(JwtUtils.getRoleFromToken(token))) {
            response.setStatus(403);
            res.put("success", false);
            res.put("message", "Forbidden");
            response.getWriter().print(gson.toJson(res));
            return false;
        }
        return true;
    }
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();
        if (!requireCustomer(request, response, res)) {
            return;
        }

        try {
            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            int bookingId = body.get("bookingId").getAsInt();
            String paymentMethod = body.get("paymentMethod").getAsString();

            BigDecimal amountToPay = dao.calculateFinalPayment(bookingId);

            boolean success = false;
            if ("CASH".equals(paymentMethod)) {
                success = dao.processFinalPayment(bookingId, paymentMethod, amountToPay);
                res.put("success", success);
            } else {
                // Endpoint này chỉ xác nhận thanh toán TIỀN MẶT. Thanh toán qua
                // cổng VNPay phải đi luồng riêng có verify chữ ký:
                // FE gọi /api/v1/payments/vnpay/create (server tự tính tiền,
                // tạo PENDING), gateway callback mới xác nhận COMPLETED.
                success = true;
                res.put("success", true);
                res.put("paymentRequired", true);
                res.put("message", "Thanh toán qua " + paymentMethod
                        + ": gọi /api/v1/payments/vnpay/create với paymentType=FINAL để lấy paymentUrl.");
            }

            res.put("finalAmount", amountToPay);

            // Notify customer, driver, dispatcher khi xác nhận thanh toán tiền mặt
            if (success && "CASH".equalsIgnoreCase(paymentMethod)) {
                try {
                    int customerAccountId = dao.getCustomerAccountIdByBookingId(bookingId);
                    if (customerAccountId != -1) {
                        dao.createNotification(customerAccountId, bookingId,
                                "Thanh toán thành công",
                                "Bạn đã thanh toán " + amountToPay.toPlainString()
                                        + "đ tiền mặt cho booking #" + bookingId + ". Cảm ơn!",
                                "PAYMENT_CASH_CONFIRMED", "IN_APP");
                    }
                    int driverAccountId = dao.getDriverAccountIdByBookingId(bookingId);
                    if (driverAccountId != -1) {
                        dao.createNotification(driverAccountId, bookingId,
                                "Khách đã xác nhận thanh toán tiền mặt",
                                "Booking #" + bookingId + " đã được thanh toán "
                                        + amountToPay.toPlainString() + "đ tiền mặt.",
                                "PAYMENT_CASH_CONFIRMED", "IN_APP");
                    }
                    java.util.List<Integer> dispatcherIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
                    for (int dispId : dispatcherIds) {
                        dao.createNotification(dispId, bookingId,
                                "Booking #" + bookingId + " đã thanh toán tiền mặt",
                                "Khách đã thanh toán " + amountToPay.toPlainString()
                                        + "đ tiền mặt cho booking #" + bookingId + ".",
                                "PAYMENT_CASH_CONFIRMED", "IN_APP");
                    }
                } catch (Exception notifEx) {
                    notifEx.printStackTrace();
                }
            }
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }
}