package controller;

import dao.AccountDAO;
import dao.AdminDashboardDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
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
 * BE-60: GET /api/v1/admin/bookings — Dashboard báo cáo tổng hành trình hệ thống (FR-5.7)
 *
 * Không có ?status=  → trả về Summary + KPI tổng quan
 * Có ?status=CANCELLED (hoặc REJECTED, COMPLETED, ONGOING...) → trả về list chi tiết
 *     kèm thông tin riêng theo status (lý do hủy/từ chối, số tiền...)
 *
 * Query params:
 *   status=CANCELLED      (optional — không có thì chỉ trả summary)
 *   fromDate=2026-06-01   (optional)
 *   toDate=2026-06-30     (optional)
 */
@WebServlet("/api/v1/admin/bookings")
public class AdminDashboardController extends HttpServlet {

    private final AdminDashboardDAO dashboardDAO = new AdminDashboardDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account admin = requireAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }

        String status = request.getParameter("status");
        String fromDate = request.getParameter("fromDate");
        String toDate = request.getParameter("toDate");

        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, ");

            // Luôn trả Summary + KPI, dù có filter status hay không —
            // để FE hiển thị dashboard tổng quan kèm bảng chi tiết cùng lúc.
            Map<String, Integer> summary = dashboardDAO.getStatusSummary(fromDate, toDate);
            BigDecimal revenue = dashboardDAO.getTotalRevenue(fromDate, toDate);
            int driverRejectCount = dashboardDAO.getDriverRejectCount(fromDate, toDate);
            Map<String, Integer> customerStats = dashboardDAO.getCustomerStats();
            List<Map<String, Object>> revenueByDay = dashboardDAO.getRevenueByDay(30);

            json.append("\"summary\": {");
            json.append("\"byStatus\": {");
            int i = 0;
            for (Map.Entry<String, Integer> entry : summary.entrySet()) {
                if (i > 0) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                i++;
            }
            json.append("}, ");
            json.append("\"totalRevenue\": ").append(revenue).append(", ");
            json.append("\"driverRejectCount\": ").append(driverRejectCount).append(", ");
            json.append("\"totalCustomers\": ").append(customerStats.get("totalCustomers")).append(", ");
            json.append("\"newCustomersToday\": ").append(customerStats.get("newCustomersToday")).append(", ");
            json.append("\"revenueByDay\": [");
            for (int j = 0; j < revenueByDay.size(); j++) {
                json.append(mapToJson(revenueByDay.get(j)));
                if (j < revenueByDay.size() - 1) json.append(",");
            }
            json.append("]");
            json.append("}");

            // Nếu có ?status=, kèm thêm danh sách chi tiết của đúng status đó
            if (status != null && !status.isEmpty()) {
                List<Map<String, Object>> bookings =
                        dashboardDAO.getBookingsByStatusDetailed(status, fromDate, toDate);

                json.append(", \"filteredStatus\": \"").append(esc(status)).append("\"");
                json.append(", \"count\": ").append(bookings.size());
                json.append(", \"data\": [");
                for (int j = 0; j < bookings.size(); j++) {
                    json.append(mapToJson(bookings.get(j)));
                    if (j < bookings.size() - 1) json.append(",");
                }
                json.append("]");
            }

            json.append("}");

            response.setStatus(200);
            out.print(json.toString());

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== Helpers =====================

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

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
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

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}