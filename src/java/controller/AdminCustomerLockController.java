package controller;

import dao.CustomerLockDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import service.CustomerLockService;
import utils.JwtUtils;

/**
 * BE-62: Admin khóa/mở khóa tài khoản Customer (FR-5.3)
 *
 * POST /api/v1/admin/customers/{id}/lock — khóa tài khoản (id = CustomerID)
 * POST /api/v1/admin/customers/{id}/unlock — mở khóa (chỉ khi nợ đã về dưới
 * ngưỡng)
 */
@WebServlet("/api/v1/admin/customers/*")
public class AdminCustomerLockController extends HttpServlet {

    private final CustomerLockService lockService = new CustomerLockService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String adminEmail = requireAdminEmail(request, response, out);
        if (adminEmail == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{customerId}/lock" | "/{customerId}/unlock"
        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{customerId}/lock hoặc /{customerId}/unlock\"}");
            return;
        }

        int customerId;
        try {
            customerId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"error\": \"customerId phải là số nguyên\"}");
            return;
        }

        String action = parts[2];

        try {
            switch (action) {
                case "lock":
                    lockService.lockCustomerAccount(customerId);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã khóa tài khoản customer #" + customerId + "\"}");
                    break;

                case "unlock":
                    lockService.unlockCustomerAccount(customerId);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã mở khóa tài khoản customer #" + customerId + "\"}");
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

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json;charset=UTF-8");

        PrintWriter out = response.getWriter();

        // CHECK ADMIN TOKEN
        String adminEmail = requireAdminEmail(request, response, out);

        if (adminEmail == null) {
            return;
        }

        try {

            List<CustomerLockDAO.CustomerInfo> list
                    = lockService.getAllCustomers();

            StringBuilder json = new StringBuilder();

            json.append("[");

            for (int i = 0; i < list.size(); i++) {

                CustomerLockDAO.CustomerInfo c = list.get(i);

                json.append("{")
                        .append("\"customerId\":")
                        .append(c.customerId)
                        .append(",")
                        .append("\"fullName\":\"")
                        .append(esc(c.fullName))
                        .append("\",")
                        .append("\"email\":\"")
                        .append(esc(c.email))
                        .append("\",")
                        .append("\"phoneNumber\":\"")
                        .append(esc(c.phoneNumber))
                        .append("\",")
                        .append("\"status\":\"")
                        .append(esc(c.status))
                        .append("\",")
                        .append("\"debt\":")
                        .append(c.debt)
                        .append(",")
                        .append("\"totalPaid\":")
                        .append(c.totalPaid)
                        .append("}");

                if (i < list.size() - 1) {
                    json.append(",");
                }
            }

            json.append("]");

            out.print(json.toString());

        } catch (Exception e) {

            response.setStatus(500);

            out.print(
                    "{\"error\":\""
                    + esc(e.getMessage())
                    + "\"}"
            );
        }
    }
}