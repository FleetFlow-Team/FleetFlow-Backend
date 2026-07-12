package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.DriverDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Account;
import service.TripTrackingService;
import utils.JwtUtils;

/**
 * Driver điều khiển lifecycle chuyến đi sau khi đã ACCEPT
 *
 * POST /api/v1/driver/trips/{bookingId}/start        — bắt đầu chuyến đi
 * POST /api/v1/driver/trips/{bookingId}/gps           — đẩy tọa độ GPS (mỗi 30s)
 * POST /api/v1/driver/trips/{bookingId}/complete      — hoàn thành chuyến đi
 * POST /api/v1/driver/trips/{bookingId}/confirm-cash  — xác nhận đã nhận tiền mặt (FINAL)
 */
@WebServlet("/api/v1/driver/trips/*")
public class DriverTripController extends HttpServlet {

    private final TripTrackingService tripService = new TripTrackingService();
    private final DriverDAO driverDAO = new DriverDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account driverAcc = requireDriverAccount(request, response, out);
        if (driverAcc == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{bookingId}/start|gps|complete"
        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{bookingId}/start|gps|complete\"}");
            return;
        }

        int bookingId;
        try {
            bookingId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"error\": \"bookingId phải là số nguyên\"}");
            return;
        }

        String action = parts[2];
        String ip = request.getRemoteAddr();

        try {
            int driverId = driverDAO.getDriverIdByAccountId((int) driverAcc.getId());
            if (driverId == -1) {
                response.setStatus(404);
                out.print("{\"error\": \"Không tìm thấy hồ sơ driver\"}");
                return;
            }

            switch (action) {
                case "start":
                    tripService.startTrip(bookingId, driverId, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã bắt đầu chuyến đi\"}");
                    break;

                case "gps": {
                    JsonObject body = readJsonBody(request);
                    if (!body.has("latitude") || !body.has("longitude")) {
                        response.setStatus(400);
                        out.print("{\"error\": \"Thiếu latitude/longitude trong body\"}");
                        return;
                    }
                    BigDecimal lat = body.get("latitude").getAsBigDecimal();
                    BigDecimal lng = body.get("longitude").getAsBigDecimal();
                    tripService.pushGpsLocation(bookingId, driverId, lat, lng);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã ghi nhận vị trí\"}");
                    break;
                }

                case "complete":
                    tripService.completeTrip(bookingId, driverId, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã hoàn thành chuyến đi\"}");
                    break;

                case "confirm-cash":
                    java.math.BigDecimal confirmedAmount = tripService.confirmCashPayment(bookingId, driverId, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã xác nhận nhận " + confirmedAmount.toPlainString() + "đ tiền mặt\"}");
                    break;

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ start, gps, complete, confirm-cash\"}");
            }

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== Helpers =====================

    private JsonObject readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private Account requireDriverAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return null;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("Driver")) {
            response.setStatus(403);
            out.print("{\"error\": \"Chỉ tài khoản Driver được truy cập chức năng này\"}");
            return null;
        }

        String email = JwtUtils.getEmailFromToken(token);
        try {
            Account acc = new AccountDAO().findByEmail(email);
            if (acc == null) {
                response.setStatus(401);
                out.print("{\"error\": \"Không tìm thấy tài khoản\"}");
                return null;
            }
            return acc;
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server khi xác thực: " + esc(e.getMessage()) + "\"}");
            return null;
        }
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}   