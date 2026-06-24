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
import service.CustomerLockService;

@WebServlet("/api/v1/bookings/*")
public class BookingController extends HttpServlet {

    private final BookingService bookingService = new BookingService();
private final CustomerLockService customerLockService = new CustomerLockService();
    private void setAccessControlHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setAccessControlHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * GET /api/v1/bookings/{id} — lấy thông tin booking
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        //setAccessControlHeaders(response);
        request.setCharacterEncoding("UTF-8");
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

            int bookingId = Integer.parseInt(pathInfo.replace("/", ""));
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
            json.append("\"customerName\":\"")
                    .append(booking.getCustomerName())
                    .append("\",");

            json.append("\"customerPhone\":\"")
                    .append(booking.getCustomerPhone())
                    .append("\",");
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
                json.append("\"distanceKm\":").append(detail.getDistanceKm()).append(",");
                json.append("\"departureTime\":\"").append(detail.getDepartureTime()).append("\"");
                // durationHours — chỉ có với bookingType = HOURLY
                if (detail.getDurationHours() != null) {
                    json.append(",\"durationHours\":").append(detail.getDurationHours());
                }
                // durationDays — chỉ có với bookingType = DAILY
                if (detail.getDurationDays() != null) {
                    json.append(",\"durationDays\":").append(detail.getDurationDays());
                }
                if (detail.getReturnTime() != null) {
                    json.append(",\"returnTime\":\"").append(detail.getReturnTime()).append("\"");
                }
                // --- Chiều về ---
                if (detail.getReturnPickupAddress() != null) {
                    json.append(",\"returnPickupAddress\":\"").append(detail.getReturnPickupAddress()).append("\"");
                    json.append(",\"returnPickupLat\":").append(detail.getReturnPickupLat());
                    json.append(",\"returnPickupLng\":").append(detail.getReturnPickupLng());
                    json.append(",\"returnDropoffAddress\":\"").append(detail.getReturnDropoffAddress()).append("\"");
                    json.append(",\"returnDropoffLat\":").append(detail.getReturnDropoffLat());
                    json.append(",\"returnDropoffLng\":").append(detail.getReturnDropoffLng());
                    json.append(",\"returnDistanceKm\":").append(detail.getReturnDistanceKm());
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
     * POST /api/v1/bookings — tạo booking mới Request body (JSON): {
     * "customerId": 1, "vehicleId": 3, "voucherId": null, "bookingType":
     * "DISTANCE", "tripDirection": "ONE_WAY", "pickupAddress": "123 Nguyễn Huệ,
     * Q1, HCM", "pickupLat": 10.776, "pickupLng": 106.700, "dropoffAddress":
     * "Vũng Tàu", "dropoffLat": 10.346, "dropoffLng": 107.084, "departureTime":
     * "2026-06-15T08:00:00", "returnTime": null }
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(response);
        request.setCharacterEncoding("UTF-8");
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

            // Parse cac field bat buoc chung cho moi loai booking
            int customerId = body.get("customerId").getAsInt();

            // Chặn tạo booking mới nếu account đang bị Admin khóa do công nợ
            customerLockService.requireCustomerNotLocked(customerId);

            int vehicleId = body.get("vehicleId").getAsInt();
            String bookingType = body.get("bookingType").getAsString();
            String tripDirection = body.get("tripDirection").getAsString();
            String departureTimeStr = body.get("departureTime").getAsString();

            // Parse optional fields
            Integer voucherId = null;
            if (body.has("voucherId") && !body.get("voucherId").isJsonNull()) {
                voucherId = body.get("voucherId").getAsInt();
            }

            Timestamp returnTime = null;
            if (body.has("returnTime") && !body.get("returnTime").isJsonNull()) {
                returnTime = Timestamp.valueOf(
                        body.get("returnTime").getAsString().replace("T", " ")
                );
            }

            // Pickup/dropoff - chi bat buoc khi bookingType = DISTANCE
            String pickupAddress = body.has("pickupAddress") && !body.get("pickupAddress").isJsonNull()
                    ? body.get("pickupAddress").getAsString() : null;
            Double pickupLat = body.has("pickupLat") && !body.get("pickupLat").isJsonNull()
                    ? body.get("pickupLat").getAsDouble() : null;
            Double pickupLng = body.has("pickupLng") && !body.get("pickupLng").isJsonNull()
                    ? body.get("pickupLng").getAsDouble() : null;

            String dropoffAddress = body.has("dropoffAddress") && !body.get("dropoffAddress").isJsonNull()
                    ? body.get("dropoffAddress").getAsString() : null;
            Double dropoffLat = body.has("dropoffLat") && !body.get("dropoffLat").isJsonNull()
                    ? body.get("dropoffLat").getAsDouble() : null;
            Double dropoffLng = body.has("dropoffLng") && !body.get("dropoffLng").isJsonNull()
                    ? body.get("dropoffLng").getAsDouble() : null;

            // durationHours/durationDays
            Integer durationHours = body.has("durationHours") && !body.get("durationHours").isJsonNull()
                    ? body.get("durationHours").getAsInt() : null;

            Integer durationDays = body.has("durationDays") && !body.get("durationDays").isJsonNull()
                    ? body.get("durationDays").getAsInt() : null;

            // Chiều về
            String returnPickupAddress = body.has("returnPickupAddress") && !body.get("returnPickupAddress").isJsonNull()
                    ? body.get("returnPickupAddress").getAsString() : null;

            Double returnPickupLat = body.has("returnPickupLat") && !body.get("returnPickupLat").isJsonNull()
                    ? body.get("returnPickupLat").getAsDouble() : null;

            Double returnPickupLng = body.has("returnPickupLng") && !body.get("returnPickupLng").isJsonNull()
                    ? body.get("returnPickupLng").getAsDouble() : null;

            String returnDropoffAddress = body.has("returnDropoffAddress") && !body.get("returnDropoffAddress").isJsonNull()
                    ? body.get("returnDropoffAddress").getAsString() : null;

            Double returnDropoffLat = body.has("returnDropoffLat") && !body.get("returnDropoffLat").isJsonNull()
                    ? body.get("returnDropoffLat").getAsDouble() : null;

            Double returnDropoffLng = body.has("returnDropoffLng") && !body.get("returnDropoffLng").isJsonNull()
                    ? body.get("returnDropoffLng").getAsDouble() : null;

            // Parse departureTime
            Timestamp departureTime = Timestamp.valueOf(
                    departureTimeStr.replace("T", " ")
            );

            // Gọi service tạo booking
            long bookingId = bookingService.createBooking(
                    customerId, vehicleId, voucherId,
                    bookingType, tripDirection,
                    pickupAddress, pickupLat, pickupLng,
                    dropoffAddress, dropoffLat, dropoffLng,
                    departureTime, returnTime,
                    durationHours, durationDays,
                    returnPickupAddress, returnPickupLat, returnPickupLng,
                    returnDropoffAddress, returnDropoffLat, returnDropoffLng
            );

            out.print("{\"success\": true, \"bookingId\": " + bookingId + ", "
                    + "\"status\": \"PENDING\", "
                    + "\"message\": \"Đặt xe thành công, chờ Dispatcher duyệt\"}");

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}");
        }

        out.flush();
    }
}
