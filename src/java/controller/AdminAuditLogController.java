package controller;

import dao.AccountDAO;
import dao.AuditLogDAO;
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
import utils.JwtUtils;

/**
 * BE-61: GET /api/v1/admin/audit-log — Xem nhật ký kiểm toán hệ thống (BR-20)
 *
 * Query params (tất cả optional):
 *   action=APPROVE_BOOKING
 *   entityName=Booking
 *   accountId=12
 *   fromDate=2026-06-01
 *   toDate=2026-06-30
 *   page=1            (default 1)
 *   pageSize=50        (default 50)
 */
@WebServlet("/api/v1/admin/audit-log")
public class AdminAuditLogController extends HttpServlet {

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private void setAccessControlHeaders(HttpServletRequest request, HttpServletResponse response) {
        String clientOrigin = request.getHeader("Origin");
        if (clientOrigin != null) {
            response.setHeader("Access-Control-Allow-Origin", clientOrigin);
        } else {
            response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5500");
        }
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
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

        Account admin = requireAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }

        String action = request.getParameter("action");
        String entityName = request.getParameter("entityName");
        String fromDate = request.getParameter("fromDate");
        String toDate = request.getParameter("toDate");

        Integer accountId = null;
        String accountIdParam = request.getParameter("accountId");
        if (accountIdParam != null && !accountIdParam.trim().isEmpty()) {
            try {
                accountId = Integer.parseInt(accountIdParam.trim());
            } catch (NumberFormatException e) {
                response.setStatus(400);
                out.print("{\"error\": \"accountId không hợp lệ\"}");
                return;
            }
        }

        int page = parseIntOrDefault(request.getParameter("page"), 1);
        int pageSize = parseIntOrDefault(request.getParameter("pageSize"), 50);

        try {
            List<Map<String, Object>> logs = auditLogDAO.findAll(
                    action, entityName, accountId, fromDate, toDate, page, pageSize);
            int total = auditLogDAO.countAll(action, entityName, accountId, fromDate, toDate);

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"page\": ").append(page)
                .append(", \"pageSize\": ").append(pageSize)
                .append(", \"total\": ").append(total)
                .append(", \"data\": [");
            for (int i = 0; i < logs.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append(mapToJson(logs.get(i)));
            }
            json.append("]}");

            response.setStatus(200);
            out.print(json.toString());

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    private int parseIntOrDefault(String s, int def) {
        if (s == null || s.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Account requireAdminAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
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

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
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
            appendValue(sb, entry.getValue());
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else {
            sb.append("\"").append(esc(value.toString())).append("\"");
        }
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}