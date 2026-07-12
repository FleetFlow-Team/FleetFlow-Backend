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

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/payments/final")
public class FinalPaymentController extends HttpServlet {

    private final ExtensionDAO dao = new ExtensionDAO();
    private final Gson gson = new Gson();
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

        try {
            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            int bookingId = body.get("bookingId").getAsInt();
            String paymentMethod = body.get("paymentMethod").getAsString();

            // ---- Code cũ của teammate (comment lại để đối chiếu, không xóa) ----
            // BigDecimal amountToPay = dao.calculateFinalPayment(bookingId);
            // boolean success = false;
            // if ("CASH".equals(paymentMethod)) {
            //     success = dao.processFinalPayment(bookingId, paymentMethod, amountToPay);
            //     res.put("success", success);
            // } else {
            //     success = true;
            //     res.put("success", true);
            // }
            // res.put("finalAmount", amountToPay);
            // ---------------------------------------------------------------------

            model.Booking booking = new dao.BookingDAO().findById(bookingId);
            // Cho phép trả ngay khi ONGOING (không cần đợi COMPLETED) — completeTrip() giờ
            // chặn tài xế hoàn thành nếu khách chưa trả xong, nên khách phải trả được trước đó.
            boolean payableStatus = booking != null
                    && ("ONGOING".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus()));
            if (!payableStatus) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Chuyến chưa bắt đầu — chưa thể thanh toán phần còn lại.");
                response.getWriter().print(gson.toJson(res));
                return;
            }

            service.PaymentService paymentService = new service.PaymentService();
            BigDecimal amountToPay = paymentService.remainingOf(bookingId);
            if (amountToPay.compareTo(BigDecimal.ZERO) <= 0) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Booking đã tất toán — không còn khoản nào phải trả.");
                response.getWriter().print(gson.toJson(res));
                return;
            }

            boolean success = true;
            if ("CASH".equals(paymentMethod)) {
                // Chỉ ghi nhận Ý ĐỊNH trả tiền mặt (PENDING) — KHÔNG hoàn tất ngay.
                // Tài xế mới là người xác nhận đã thực nhận tiền (xem confirm-cash),
                // chống khách tự khai khống để vượt gate hoàn thành chuyến.
                paymentService.getOrCreatePending(bookingId, "FINAL", "CASH", amountToPay);
                res.put("success", true);
                res.put("message", "Đã ghi nhận yêu cầu thanh toán tiền mặt — tài xế sẽ xác nhận khi nhận đủ tiền.");
            } else {
                // VNPay: FE gọi tiếp /payments/vnpay/create; ở đây chỉ báo số tiền
                res.put("success", true);
            }

            res.put("finalAmount", amountToPay);

            // Báo cho tài xế + dispatcher biết khách CHỌN trả tiền mặt (chưa phải đã trả)
            if (success && "CASH".equalsIgnoreCase(paymentMethod)) {
                try {
                    int driverAccountId = dao.getDriverAccountIdByBookingId(bookingId);
                    if (driverAccountId != -1) {
                        dao.createNotification(driverAccountId, bookingId,
                                "Nhắc thu tiền mặt",
                                "Khách chọn thanh toán tiền mặt cho chuyến #" + bookingId
                                        + " rồi nha. Nhờ bạn thu " + amountToPay.toPlainString()
                                        + "đ từ khách rồi bấm \"Xác nhận đã nhận tiền mặt\" trong app nhé!",
                                "PAYMENT_CASH_PENDING", "IN_APP");
                    }
                    java.util.List<Integer> dispatcherIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
                    for (int dispId : dispatcherIds) {
                        dao.createNotification(dispId, bookingId,
                                "Booking #" + bookingId + " chọn thanh toán tiền mặt",
                                "Khách chọn thanh toán " + amountToPay.toPlainString()
                                        + "đ tiền mặt cho booking #" + bookingId + " — đang chờ tài xế xác nhận.",
                                "PAYMENT_CASH_PENDING", "IN_APP");
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