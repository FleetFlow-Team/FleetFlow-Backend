package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.DispatcherDriverDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

/**
 * Dispatcher xem danh sách tài xế (deadline #4): tình trạng hoạt động, trạng
 * thái duyệt, đánh giá TB, số chuyến đã nhận/đã hoàn thành.
 *
 *  GET /api/v1/dispatcher/drivers   (Bearer token Dispatcher hoặc Admin)
 */
@WebServlet("/api/v1/dispatcher/drivers")
public class DispatcherDriverController extends HttpServlet {

    private final DispatcherDriverDAO dao = new DispatcherDriverDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /** Cho phép Dispatcher hoặc Admin. */
    private boolean requireDispatcherOrAdmin(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"success\":false,\"error\":\"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return false;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("Dispatcher") || role.equalsIgnoreCase("Admin"))) {
            response.setStatus(403);
            out.print("{\"success\":false,\"error\":\"Chỉ Dispatcher hoặc Admin được truy cập chức năng này\"}");
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        if (!requireDispatcherOrAdmin(request, response, out)) {
            return;
        }
        try {
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", dao.listDriversWithStats());
            out.print(gson.toJson(res));
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\":false,\"error\":\"Lỗi server\"}");
        }
    }
}
