package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Booking;
import model.BookingDetail;
import service.BookingService;

@WebServlet("/api/v1/bookings/*")
public class BookingController extends HttpServlet {

    private final BookingService bookingService = new BookingService();

    /**
     * GET /api/v1/bookings/{id} — lấy thông tin booking
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(400);
                out.print("{\"error\": \"Thiếu BookingID\"}");
                return;
            }

            long bookingId = Long.parseLong(pathInfo.replace("/", ""));
            Booking booking = bookingService.getBookingById(bookingId);

            if (booking == null) {
                response.setStatus(404);
                out.print("{\"error\": \"Không tìm thấy booking\"}");
                return;
            }

            BookingDetail detail = bookingService.getBookingDetail(bookingId);

            // Build JSON response
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"bookingId\":").append(booking.getId()).append(",");
            json.append("\"customerId\":").append(booking.getCustomerId()).append(",");
            json.append("\"vehicleId\":").append(booking.getVehicleId()).append(",");
            json.append("\"bookingType\":\"").append(booking.getBookingType()).append("\",");
            json.append("\"tripDirection\":\"").append(booking.getTripDirection()).append("\",");
            json.append("\"status\":\"").append(booking.getStatus()).append("\"");

            if (detail != null) {
                json.append(",\"detail\":{");
                json.append("\"pickupAddress\":\"").append(detail.getPickupAddress()).append("\",");
                json.append("\"pickupLat\":").append(detail.getPickupLat()).append(",");
                json.append("\"pickupLng\":").append(detail.getPickupLng()).append(",");
                json.append("\"dropoffAddress\":\"").append(detail.getDropoffAddress()).append("\",");
                json.append("\"dropoffLat\":").append(detail.getDropoffLat()).append(",");
                json.append("\"dropoffLng\":").append(detail.getDropoffLng()).append(",");
                json.append("\"departureTime\":\"").append(detail.getDepartureTime()).append("\"");
                if (detail.getReturnTime() != null) {
                    json.append(",\"returnTime\":\"").append(detail.getReturnTime()).append("\"");
                }
                json.append("}");
            }

            json.append("}");
            out.print(json.toString());

        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"error\": \"BookingID không hợp lệ\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + e.getMessage() + "\"}");
        }

        out.flush();
    }

    /**
     * POST /api/v1/bookings — tạo booking mới
     * Request body (JSON):
     * {
     *   "customerId": 1,
     *   "vehicleId": 3,
     *   "voucherId": null,
     *   "bookingType": "DISTANCE",
     *   "tripDirection": "ONE_WAY",
     *   "pickupAddress": "123 Nguyễn Huệ, Q1, HCM",
     *   "pickupLat": 10.776,
     *   "pickupLng": 106.700,
     *   "dropoffAddress": "Vũng Tàu",
     *   "dropoffLat": 10.346,
     *   "dropoffLng": 107.084,
     *   "departureTime": "2026-06-15T08:00:00",
     *   "returnTime": null
     * }
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            // Đọc request body
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();

            // Parse các field bắt buộc
            long customerId   = body.get("customerId").getAsLong();
            long vehicleId    = body.get("vehicleId").getAsLong();
            String bookingType   = body.get("bookingType").getAsString();
            String tripDirection = body.get("tripDirection").getAsString();
            String pickupAddress  = body.get("pickupAddress").getAsString();
            double pickupLat      = body.get("pickupLat").getAsDouble();
            double pickupLng      = body.get("pickupLng").getAsDouble();
            String dropoffAddress = body.get("dropoffAddress").getAsString();
            double dropoffLat     = body.get("dropoffLat").getAsDouble();
            double dropoffLng     = body.get("dropoffLng").getAsDouble();
            String departureTimeStr = body.get("departureTime").getAsString();

            // Parse optional fields
            Long voucherId = null;
            if (body.has("voucherId") && !body.get("voucherId").isJsonNull()) {
                voucherId = body.get("voucherId").getAsLong();
            }

            Timestamp returnTime = null;
            if (body.has("returnTime") && !body.get("returnTime").isJsonNull()) {
                returnTime = Timestamp.valueOf(
                    body.get("returnTime").getAsString().replace("T", " ")
                );
            }

            // Parse departureTime
            Timestamp departureTime = Timestamp.valueOf(
                departureTimeStr.replace("T", " ")
            );

            // Gọi BookingService — validate maps + insert DB
            long bookingId = bookingService.createBooking(
                customerId, vehicleId, voucherId,
                bookingType, tripDirection,
                pickupAddress, pickupLat, pickupLng,
                dropoffAddress, dropoffLat, dropoffLng,
                departureTime, returnTime
            );

            // Trả về response thành công
            out.print("{\"success\": true, \"bookingId\": " + bookingId + ", "
                    + "\"status\": \"PENDING\", "
                    + "\"message\": \"Đặt xe thành công, chờ Dispatcher duyệt\"}");

        } catch (IllegalArgumentException e) {
            // Lỗi validate (khoảng cách, thời gian)
            response.setStatus(400);
            out.print("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}");
        }

        out.flush();
    }

    /**
     * PATCH /api/v1/bookings/{id}/status — cập nhật trạng thái
     * Body: { "status": "APPROVED" }
     */
    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.contains("/")) {
                response.setStatus(400);
                out.print("{\"error\": \"Thiếu BookingID\"}");
                return;
            }

            // Path: /{id}/status
            String[] parts = pathInfo.split("/");
            long bookingId = Long.parseLong(parts[1]);

            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String status = body.get("status").getAsString();

            bookingService.updateBookingStatus(bookingId, status);

            out.print("{\"success\": true, \"bookingId\": " + bookingId
                    + ", \"status\": \"" + status + "\"}");

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"" + e.getMessage() + "\"}");
        }

        out.flush();
    }
}