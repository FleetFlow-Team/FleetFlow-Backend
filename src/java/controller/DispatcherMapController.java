package controller;

import dao.AccountDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Account;
import model.TripGpsLog;
import service.TripTrackingService;
import utils.JwtUtils;

/**
 * Dispatcher xem bản đồ real-time tất cả xe đang chạy
 *
 * GET /api/v1/dispatcher/map — vị trí mới nhất của mọi booking đang ONGOING
 */
@WebServlet("/api/v1/dispatcher/map")
public class DispatcherMapController extends HttpServlet {

    private final TripTrackingService tripService = new TripTrackingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account dispatcher = requireDispatcherAccount(request, response, out);
        if (dispatcher == null) {
            return;
        }

        try {
            List<TripGpsLog> ongoing = tripService.getOngoingTripsMap();

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"data\": [");
            for (int i = 0; i < ongoing.size(); i++) {
                TripGpsLog g = ongoing.get(i);
                json.append("{");
                json.append("\"bookingId\":").append(g.getBookingId()).append(",");
                json.append("\"latitude\":").append(g.getLatitude()).append(",");
                json.append("\"longitude\":").append(g.getLongitude()).append(",");
                json.append("\"recordedAt\":\"").append(g.getRecordedAt()).append("\"");
                json.append("}");
                if (i < ongoing.size() - 1) {
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

    // ===================== Helpers =====================

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

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

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}