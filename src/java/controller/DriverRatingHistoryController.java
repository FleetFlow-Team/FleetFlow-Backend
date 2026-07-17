package controller;

import dao.AccountDAO;
import dao.DriverDAO;
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
 * GET /api/v1/driver/ratings — Tài xế xem điểm trung bình + danh sách đánh giá của
 * khách hàng về mình, để rút kinh nghiệm.
 */
@WebServlet("/api/v1/driver/ratings")
public class DriverRatingHistoryController extends HttpServlet {

    private final RatingDAO ratingDAO = new RatingDAO();
    private final DriverDAO driverDAO = new DriverDAO();

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();

        Account driverAcc = requireDriverAccount(request, response, out);
        if (driverAcc == null) {
            return;
        }

        try {
            int driverId = driverDAO.getDriverIdByAccountId((int) driverAcc.getId());
            if (driverId == -1) {
                response.setStatus(404);
                out.print("{\"success\": false, \"message\": \"Không tìm thấy hồ sơ driver\"}");
                return;
            }

            Map<String, Object> summary = ratingDAO.getDriverRatingSummary(driverId);
            List<Map<String, Object>> data = ratingDAO.getRatingsForDriver(driverId);

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, ");
            json.append("\"averageRating\": ").append(summary.getOrDefault("averageRating", "null")).append(", ");
            json.append("\"ratingCount\": ").append(summary.getOrDefault("ratingCount", 0)).append(", ");
            json.append("\"data\": [");
            for (int i = 0; i < data.size(); i++) {
                json.append(rowToJson(data.get(i)));
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

    private String rowToJson(Map<String, Object> m) {
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

    private Account requireDriverAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"success\": false, \"message\": \"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return null;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("Driver")) {
            response.setStatus(403);
            out.print("{\"success\": false, \"message\": \"Chỉ tài khoản Driver được truy cập chức năng này\"}");
            return null;
        }

        String email = JwtUtils.getEmailFromToken(token);
        try {
            Account acc = new AccountDAO().findByEmail(email);
            if (acc == null) {
                response.setStatus(401);
                out.print("{\"success\": false, \"message\": \"Không tìm thấy tài khoản\"}");
                return null;
            }
            return acc;
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\": false, \"message\": \"Lỗi server khi xác thực: " + esc(e.getMessage()) + "\"}");
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
