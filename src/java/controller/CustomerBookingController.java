package controller;

import model.Booking;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Account;
import dao.CustomerBookingDAO.BookingRow;

import service.CustomerBookingService;
import service.CustomerBookingService.CancelResult;
import service.CustomerBookingService.PriceResult;
import service.CustomerBookingService.VoucherResult;

/**
 * BE-23: GET /api/v1/customer/bookings — lịch sử đặt xe BE-25: POST
 * /api/v1/customer/bookings/cancel — hủy booking + tính phạt BE-26: POST
 * /api/v1/customer/bookings/check-price — tính giá ước tính BE-27: POST
 * /api/v1/customer/vouchers/apply — áp dụng voucher
 *
 * BE-24: GET /api/v1/bookings/{id} — đã có trong BookingController
 */
@WebServlet("/api/v1/customer/*")
public class CustomerBookingController extends HttpServlet {

    private final CustomerBookingService service = new CustomerBookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        String pathInfo = request.getPathInfo(); // "/bookings"

        if ("/bookings".equals(pathInfo)) {
            handleGetBookingHistory(request, response, out);
        } else {
            response.setStatus(404);
            out.print("{\"error\": \"Endpoint không tồn tại\"}");
        }
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        String pathInfo = request.getPathInfo();

        switch (pathInfo == null ? "" : pathInfo) {
            case "/bookings/cancel":
                handleCancel(request, response, out);
                break;
            case "/bookings/check-price":
                handleCheckPrice(request, response, out);
                break;
            case "/vouchers/apply":
                handleApplyVoucher(request, response, out);
                break;
            default:
                response.setStatus(404);
                out.print("{\"error\": \"Endpoint không tồn tại\"}");
        }
        out.flush();
    }

    // ===================== BE-23: GET /api/v1/customer/bookings =====================
    private void handleGetBookingHistory(HttpServletRequest request,
            HttpServletResponse response, PrintWriter out) throws IOException {

        int customerId = getCustomerIdFromSession(request);
        if (customerId == -1) {
            String param = request.getParameter("customerId");
            if (param == null) {
                response.setStatus(401);
                out.print("{\"error\": \"Chưa đăng nhập\"}");
                return;
            }
            customerId = Integer.parseInt(param);
        }

        try {
            List<BookingRow> bookings = service.getBookingHistory(customerId);
            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"data\": [");

            for (int i = 0; i < bookings.size(); i++) {
                BookingRow b = bookings.get(i);
                json.append("{");
                json.append("\"bookingId\":").append(b.bookingId).append(",");
                json.append("\"vehicleId\":").append(b.vehicleId).append(",");
                json.append("\"vehicleName\":\"").append(esc(b.brand + " " + b.model)).append("\",");
                json.append("\"licensePlate\":\"").append(esc(b.licensePlate)).append("\",");
                json.append("\"bookingType\":\"").append(esc(b.bookingType)).append("\",");
                json.append("\"tripDirection\":\"").append(esc(b.tripDirection)).append("\",");
                json.append("\"status\":\"").append(esc(b.status)).append("\",");
                json.append("\"pickupAddress\":\"").append(esc(b.pickupAddress)).append("\",");
                json.append("\"dropoffAddress\":\"").append(esc(b.dropoffAddress)).append("\",");
                json.append("\"departureTime\":\"").append(b.departureTime).append("\",");
                json.append("\"distanceKm\":").append(b.distanceKm != null ? b.distanceKm : 0).append(",");
                json.append("\"createdAt\":\"").append(b.createdAt).append("\"");
                json.append("}");
                if (i < bookings.size() - 1) {
                    json.append(",");
                }
            }

            json.append("]}");
            response.setStatus(200);
            out.print(json.toString());

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== BE-25: POST /api/v1/customer/bookings/cancel =====================
    // Body: { "bookingId": 1, "reason": "Thay đổi kế hoạch" }
    private void handleCancel(HttpServletRequest request,
            HttpServletResponse response, PrintWriter out) throws IOException {
        try {
            JsonObject body = readBody(request);
            int bookingId = body.get("bookingId").getAsInt();
            String reason = body.has("reason") ? body.get("reason").getAsString() : "";

            int customerId = getCustomerIdFromSession(request);
            if (customerId == -1) {
                customerId = body.has("customerId") ? body.get("customerId").getAsInt() : -1;
            }
            if (customerId == -1) {
                response.setStatus(401);
                out.print("{\"error\": \"Chưa đăng nhập\"}");
                return;
            }

            CancelResult result = service.cancelBooking(bookingId, customerId, reason);

            response.setStatus(200);
            out.print("{\"success\": true,"
                    + "\"bookingId\":" + result.bookingId + ","
                    + "\"penaltyPercent\":" + result.penaltyPercent + ","
                    + "\"penaltyAmount\":" + result.penaltyAmount + ","
                    + "\"message\":\"Hủy booking thành công\"}");

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== BE-26: POST /api/v1/customer/bookings/check-price =====================
    // Body: { "vehicleId": 3, "bookingType": "DISTANCE", "tripDirection": "ONE_WAY",
    //         "distanceKm": 120.5, "durationHours": 0, "durationDays": 0,
    //         "departureTime": "2026-06-20T08:00:00" }
    private void handleCheckPrice(HttpServletRequest request,
            HttpServletResponse response, PrintWriter out) throws IOException {
        try {
            JsonObject body = readBody(request);
            int vehicleId = body.get("vehicleId").getAsInt();
            String bookingType = body.get("bookingType").getAsString();
            String tripDirection = body.get("tripDirection").getAsString();
            double distanceKm = body.has("distanceKm") ? body.get("distanceKm").getAsDouble() : 0;
            int durationHours = body.has("durationHours") ? body.get("durationHours").getAsInt() : 0;
            int durationDays = body.has("durationDays") ? body.get("durationDays").getAsInt() : 0;

            Timestamp departureTime = null;
            if (body.has("departureTime") && !body.get("departureTime").isJsonNull()) {
                departureTime = Timestamp.valueOf(
                        body.get("departureTime").getAsString().replace("T", " "));
            }

            PriceResult result = service.checkPrice(vehicleId, bookingType, tripDirection,
                    distanceKm, durationHours, durationDays, departureTime);

            response.setStatus(200);
            out.print("{\"success\": true,"
                    + "\"ruleId\":" + result.ruleId + ","
                    + "\"baseFare\":" + result.baseFare + ","
                    + "\"weekendSurcharge\":" + result.weekendSurcharge + ","
                    + "\"estimatedTotal\":" + result.estimatedTotal + ","
                    + "\"deposit30Percent\":" + result.deposit30Percent + "}");

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== BE-27: POST /api/v1/customer/vouchers/apply =====================
    // Body: { "code": "SUMMER20", "customerId": 1,
    //         "estimatedTotal": 500000, "vehicleTypeId": 2 }
    private void handleApplyVoucher(HttpServletRequest request,
            HttpServletResponse response, PrintWriter out) throws IOException {
        try {
            JsonObject body = readBody(request);
            String code = body.get("code").getAsString();
            BigDecimal estimatedTotal = body.get("estimatedTotal").getAsBigDecimal();
            int vehicleTypeId = body.has("vehicleTypeId") ? body.get("vehicleTypeId").getAsInt() : 0;

            int customerId = getCustomerIdFromSession(request);
            if (customerId == -1) {
                customerId = body.has("customerId") ? body.get("customerId").getAsInt() : -1;
            }
            if (customerId == -1) {
                response.setStatus(401);
                out.print("{\"error\": \"Chưa đăng nhập\"}");
                return;
            }

            VoucherResult result = service.applyVoucher(code, customerId, estimatedTotal, vehicleTypeId);

            response.setStatus(200);
            out.print("{\"success\": true,"
                    + "\"voucherId\":" + result.voucherId + ","
                    + "\"code\":\"" + esc(result.code) + "\","
                    + "\"discountAmount\":" + result.discountAmount + ","
                    + "\"finalTotal\":" + result.finalTotal + "}");

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== Helpers =====================
    private int getCustomerIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return -1;
        }
        Account acc = (Account) session.getAttribute("account");
        if (acc == null) {
            return -1;
        }
        // Lấy customerId từ session attribute (set khi login)
        Object cid = session.getAttribute("customerId");
        if (cid != null) {
            return (int) cid;
        }
        return -1;
    }

    private JsonObject readBody(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
