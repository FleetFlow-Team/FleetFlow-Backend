package controller;

import dao.AccountDAO;
import dao.RatingDAO;
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
 * GET /api/v1/admin/ratings — Admin xem & quản lý chất lượng qua rating.
 *
 * Query params:
 *   type=customer|driver   (default customer) — customer: khách đánh giá tài xế/xe;
 *                                                 driver: tài xế đánh giá khách
 *   driverId, customerId, bookingId  (optional filter)
 *   lowOnly=true                     (optional) — chỉ lấy rating thấp (<= 2 sao)
 *   fromDate, toDate                 (optional, yyyy-MM-dd)
 *
 * Luôn kèm "driverQuality": điểm trung bình + số rating thấp theo từng tài xế,
 * xếp tệ nhất lên đầu, để admin phát hiện tài xế có vấn đề về chất lượng.
 */
@WebServlet("/api/v1/admin/ratings")
public class AdminRatingController extends HttpServlet {

    private final RatingDAO ratingDAO = new RatingDAO();

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

        String type = request.getParameter("type");
        if (type == null || type.isEmpty()) {
            type = "customer";
        }
        Integer driverId = parseIntOrNull(request.getParameter("driverId"));
        Integer customerId = parseIntOrNull(request.getParameter("customerId"));
        Integer bookingId = parseIntOrNull(request.getParameter("bookingId"));
        boolean lowOnly = "true".equalsIgnoreCase(request.getParameter("lowOnly"));
        String fromDate = request.getParameter("fromDate");
        String toDate = request.getParameter("toDate");

        try {
            List<Map<String, Object>> driverQuality = ratingDAO.getDriverQualityStats(fromDate, toDate);

            Map<String, Object> summary;
            List<Map<String, Object>> data;
            if ("driver".equalsIgnoreCase(type)) {
                summary = ratingDAO.getDriverRatingSummaryForAdmin(driverId, customerId, bookingId, fromDate, toDate);
                data = ratingDAO.getDriverRatingsForAdmin(driverId, customerId, bookingId, lowOnly, fromDate, toDate);
            } else {
                type = "customer";
                summary = ratingDAO.getCustomerRatingSummaryForAdmin(driverId, customerId, bookingId, fromDate, toDate);
                data = ratingDAO.getCustomerRatingsForAdmin(driverId, customerId, bookingId, lowOnly, fromDate, toDate);
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"type\": \"").append(esc(type)).append("\", ");

            json.append("\"driverQuality\": [");
            for (int i = 0; i < driverQuality.size(); i++) {
                json.append(mapToJson(driverQuality.get(i)));
                if (i < driverQuality.size() - 1) {
                    json.append(",");
                }
            }
            json.append("], ");

            json.append("\"summary\": ").append(mapToJson(summary)).append(", ");
            json.append("\"count\": ").append(data.size()).append(", ");
            json.append("\"data\": [");
            for (int i = 0; i < data.size(); i++) {
                json.append(mapToJson(data.get(i)));
                if (i < data.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]}");

            response.setStatus(200);
            out.print(json.toString());
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\": false, \"message\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== Helpers =====================

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
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
