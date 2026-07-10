package controller;

import dao.AccountDAO;
import dao.ExtensionDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

/**
 * Admin khóa/mở khóa tài khoản Dispatcher.
 *
 * GET  /api/v1/admin/dispatchers          — danh sách toàn bộ Dispatcher
 * POST /api/v1/admin/dispatchers/{id}/lock   — khóa tài khoản (id = AccountID)
 * POST /api/v1/admin/dispatchers/{id}/unlock — mở khóa tài khoản
 */
@WebServlet("/api/v1/admin/dispatchers/*")
public class AdminDispatcherController extends HttpServlet {

    private final AccountDAO accountDAO = new AccountDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();

    private void setAccessControlHeaders(HttpServletRequest request, HttpServletResponse response) {
        String clientOrigin = request.getHeader("Origin");

        if (clientOrigin != null) {
            response.setHeader("Access-Control-Allow-Origin", clientOrigin);
        } else {
            response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5500");
        }

        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        if (requireAdminEmail(request, response, out) == null) {
            return;
        }

        try {
            List<Map<String, Object>> dispatchers = accountDAO.getAllDispatchers();
            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"data\": [");
            for (int i = 0; i < dispatchers.size(); i++) {
                json.append(mapToJson(dispatchers.get(i)));
                if (i < dispatchers.size() - 1) json.append(",");
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

        setAccessControlHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        if (requireAdminEmail(request, response, out) == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{accountId}/lock" | "/{accountId}/unlock"
        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{accountId}/lock hoặc /{accountId}/unlock\"}");
            return;
        }

        int accountId;
        try {
            accountId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"error\": \"accountId phải là số nguyên\"}");
            return;
        }

        String action = parts[2];

        try {
            requireRole(accountId, "Dispatcher");

            switch (action) {
                case "lock":
                    accountDAO.lockAccountById(accountId);
                    extensionDAO.createNotification(accountId, null,
                            "Tài khoản đã bị tạm khóa",
                            "Tài khoản dispatcher của bạn đã bị Admin tạm khóa. Vui lòng liên hệ để được hỗ trợ mở lại.",
                            "ACCOUNT_LOCKED", "BOTH");
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã khóa tài khoản dispatcher #" + accountId + "\"}");
                    break;

                case "unlock":
                    accountDAO.unlockAccountById(accountId);
                    extensionDAO.createNotification(accountId, null,
                            "Tài khoản đã được mở khóa",
                            "Tài khoản dispatcher của bạn đã được mở khóa.",
                            "ACCOUNT_UNLOCKED", "BOTH");
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã mở khóa tài khoản dispatcher #" + accountId + "\"}");
                    break;

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ lock, unlock\"}");
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

    private void requireRole(int accountId, String expectedRole) throws Exception {
        String role = accountDAO.getRoleNameByAccountId(accountId);
        if (role == null) {
            throw new IllegalArgumentException("Không tìm thấy account #" + accountId);
        }
        if (!expectedRole.equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("Account #" + accountId + " không phải " + expectedRole
                    + " (đang là " + role + ")");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private String requireAdminEmail(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return null;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("Admin")) {
            response.setStatus(403);
            out.print("{\"error\": \"Chỉ tài khoản Admin được truy cập chức năng này\"}");
            return null;
        }
        return JwtUtils.getEmailFromToken(token);
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String mapToJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            if (i > 0) sb.append(",");
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
}
