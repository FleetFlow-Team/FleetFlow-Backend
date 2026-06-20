package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.DriverDAO;
import dao.DriverJobBroadcastDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Account;
import model.DriverJobBroadcast;
import service.BookingWorkflowService;
import utils.JwtUtils;

/**
 * Driver xem lệnh dispatch đang chờ + accept/reject
 *
 * GET  /api/v1/driver/dispatch/pending          — danh sách lệnh PENDING của driver đang login
 * POST /api/v1/driver/dispatch/{broadcastId}/accept  — accept lệnh
 * POST /api/v1/driver/dispatch/{broadcastId}/reject  — reject lệnh
 */
@WebServlet("/api/v1/driver/dispatch/*")
public class DriverDispatchController extends HttpServlet {

    private final DriverJobBroadcastDAO broadcastDAO = new DriverJobBroadcastDAO();
    private final DriverDAO driverDAO = new DriverDAO();
    private final BookingWorkflowService workflowService = new BookingWorkflowService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account driverAcc = requireDriverAccount(request, response, out);
        if (driverAcc == null) {
            return;
        }

        String pathInfo = request.getPathInfo();
        if (!"/pending".equals(pathInfo)) {
            response.setStatus(404);
            out.print("{\"error\": \"Endpoint không tồn tại\"}");
            return;
        }

        try {
            int driverId = driverDAO.getDriverIdByAccountId((int) driverAcc.getId());
            if (driverId == -1) {
                response.setStatus(404);
                out.print("{\"error\": \"Không tìm thấy hồ sơ driver\"}");
                return;
            }

            List<DriverJobBroadcast> pending = broadcastDAO.getPendingForDriver(driverId);

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"data\": [");
            for (int i = 0; i < pending.size(); i++) {
                DriverJobBroadcast b = pending.get(i);
                json.append("{");
                json.append("\"broadcastId\":").append(b.getId()).append(",");
                json.append("\"bookingId\":").append(b.getBookingId()).append(",");
                json.append("\"dispatchedAt\":\"").append(b.getDispatchedAt()).append("\"");
                json.append("}");
                if (i < pending.size() - 1) {
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

        Account driverAcc = requireDriverAccount(request, response, out);
        if (driverAcc == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{broadcastId}/accept" | "/{broadcastId}/reject"
        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{broadcastId}/accept|reject\"}");
            return;
        }

        int broadcastId;
        try {
            broadcastId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"error\": \"broadcastId phải là số nguyên\"}");
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
                case "accept":
                    workflowService.driverAccept(broadcastId, driverId, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã nhận chuyến\"}");
                    break;

                case "reject": {
                    String reason = readReason(request);
                    workflowService.driverReject(broadcastId, driverId, reason, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã từ chối chuyến\"}");
                    break;
                }

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ accept, reject\"}");
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