package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.BookingDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Account;
import service.BookingWorkflowService;
import utils.JwtUtils;

/**
 * Dispatcher quản lý duyệt booking + dispatch driver
 *
 * POST /api/v1/dispatcher/bookings/{id}/approve   — duyệt booking PENDING → APPROVED
 * POST /api/v1/dispatcher/bookings/{id}/reject    — từ chối booking PENDING → REJECTED
 * POST /api/v1/dispatcher/bookings/{id}/dispatch  — chỉ định driver cho booking APPROVED
 *
 * Xác thực bằng JWT Bearer token, role phải là Dispatcher (hoặc Admin).
 */
@WebServlet("/api/v1/dispatcher/bookings/*")
public class DispatcherBookingController extends HttpServlet {

    private final BookingWorkflowService workflowService = new BookingWorkflowService();
    private final BookingDAO bookingDAO = new BookingDAO();

    /**
     * GET /api/v1/dispatcher/bookings/pending — danh sách booking đang chờ duyệt (PENDING)
     * GET /api/v1/dispatcher/bookings?status=APPROVED — xem theo status khác nếu cần
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account dispatcher = requireDispatcherAccount(request, response, out);
        if (dispatcher == null) {
            return;
        }

        String pathInfo = request.getPathInfo();
        String status;

        if ("/pending".equals(pathInfo)) {
            status = "PENDING";
        } else if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            String statusParam = request.getParameter("status");
            status = (statusParam != null && !statusParam.isEmpty()) ? statusParam : "PENDING";
        } else {
            response.setStatus(404);
            out.print("{\"error\": \"Endpoint không tồn tại\"}");
            return;
        }

        try {
            List<Map<String, Object>> bookings = bookingDAO.findByStatusWithDetail(status);

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"count\": ").append(bookings.size()).append(", \"data\": [");

            for (int i = 0; i < bookings.size(); i++) {
                json.append(mapToJson(bookings.get(i)));
                if (i < bookings.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]}");

            response.setStatus(200);
            out.print(json.toString());

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account dispatcher = requireDispatcherAccount(request, response, out);
        if (dispatcher == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{id}/approve" | "/{id}/reject" | "/{id}/dispatch"
        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{bookingId}/approve|reject|dispatch\"}");
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
            switch (action) {
                case "approve":
                    workflowService.approveBooking(bookingId, (int) dispatcher.getId(), ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã duyệt booking #" + bookingId + "\"}");
                    break;

                case "reject": {
                    String reason = readReason(request);
                    workflowService.rejectBooking(bookingId, (int) dispatcher.getId(), reason, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã từ chối booking #" + bookingId + "\"}");
                    break;
                }

                case "dispatch": {
                    JsonObject body = readJsonBody(request);
                    if (!body.has("driverId")) {
                        response.setStatus(400);
                        out.print("{\"error\": \"Thiếu driverId trong body\"}");
                        return;
                    }
                    int driverId = body.get("driverId").getAsInt();
                    long broadcastId = workflowService.dispatchDriver(
                            bookingId, driverId, (int) dispatcher.getId(), ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"broadcastId\": " + broadcastId
                            + ", \"message\": \"Đã dispatch driver #" + driverId
                            + " cho booking #" + bookingId + "\"}");
                    break;
                }

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ approve, reject, dispatch\"}");
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

    private String readReason(HttpServletRequest request) throws IOException {
        JsonObject body = readJsonBody(request);
        return body.has("reason") && !body.get("reason").isJsonNull()
                ? body.get("reason").getAsString() : null;
    }

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

    /**
     * Yêu cầu role Dispatcher hoặc Admin (Admin có thể thay Dispatcher khi cần demo/test).
     */
    private Account requireDispatcherAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return null;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("Dispatcher") || role.equalsIgnoreCase("Admin"))) {
            response.setStatus(403);
            out.print("{\"error\": \"Chỉ Dispatcher hoặc Admin được truy cập chức năng này\"}");
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

    private String mapToJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(esc(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value.toString());
            } else {
                sb.append("\"").append(esc(value.toString())).append("\"");
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}