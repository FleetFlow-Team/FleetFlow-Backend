package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.AdminAccountDAO;
import dao.CustomerLockDAO;
import dao.ExtensionDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

/**
 * Admin khóa/mở khóa tài khoản Driver và Dispatcher (deadline #3).
 *
 * Customer đã có luồng riêng kèm rào công nợ ở /admin/customers/* — controller
 * này chỉ dành cho Driver/Dispatcher (không rào nợ). Lock = Account.Status='LOCKED'.
 *
 *  GET  /api/v1/admin/accounts?role=Driver|Dispatcher  — danh sách + trạng thái
 *  POST /api/v1/admin/accounts/{accountId}/lock        — khóa
 *  POST /api/v1/admin/accounts/{accountId}/unlock      — mở khóa
 */
@WebServlet("/api/v1/admin/accounts/*")
public class AdminAccountLockController extends HttpServlet {

    private final CustomerLockDAO lockDAO = new CustomerLockDAO();
    private final AdminAccountDAO accountDAO = new AdminAccountDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /** Trả email admin nếu token hợp lệ + role Admin; nếu không tự ghi lỗi và trả null. */
    private String requireAdmin(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"success\":false,\"error\":\"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return null;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("Admin")) {
            response.setStatus(403);
            out.print("{\"success\":false,\"error\":\"Chỉ tài khoản Admin được truy cập chức năng này\"}");
            return null;
        }
        return JwtUtils.getEmailFromToken(token);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        if (requireAdmin(request, response, out) == null) {
            return;
        }
        String role = request.getParameter("role");
        if (role == null || !(role.equalsIgnoreCase("Driver") || role.equalsIgnoreCase("Dispatcher"))) {
            response.setStatus(400);
            out.print("{\"success\":false,\"error\":\"Thiếu/không hợp lệ tham số role (chỉ Driver hoặc Dispatcher)\"}");
            return;
        }
        try {
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("data", accountDAO.listByRole(role));
            out.print(gson.toJson(res));
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\":false,\"error\":\"Lỗi server\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        if (requireAdmin(request, response, out) == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{accountId}/lock" | "/{accountId}/unlock"
        String[] parts = (pathInfo == null) ? new String[0] : pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"success\":false,\"error\":\"Path không hợp lệ. Dùng /{accountId}/lock hoặc /{accountId}/unlock\"}");
            return;
        }
        int accountId;
        try {
            accountId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"success\":false,\"error\":\"accountId phải là số nguyên\"}");
            return;
        }
        String action = parts[2];

        try {
            // Chỉ cho phép khóa/mở Driver hoặc Dispatcher (customer dùng /admin/customers)
            String role = accountDAO.getRole(accountId);
            if (role == null) {
                response.setStatus(404);
                out.print("{\"success\":false,\"error\":\"Không tìm thấy account #" + accountId + "\"}");
                return;
            }
            if (!(role.equalsIgnoreCase("Driver") || role.equalsIgnoreCase("Dispatcher"))) {
                response.setStatus(400);
                out.print("{\"success\":false,\"error\":\"Chỉ hỗ trợ khóa Driver/Dispatcher. Account #"
                        + accountId + " có role " + role + ".\"}");
                return;
            }

            switch (action) {
                case "lock":
                    lockDAO.lockAccount(accountId);
                    extensionDAO.createNotification(accountId, null, "Tài khoản đã bị tạm khóa",
                            "Tài khoản " + role + " của bạn đã bị Admin tạm khóa. Vui lòng liên hệ để được hỗ trợ.",
                            "ACCOUNT_LOCKED", "IN_APP");
                    out.print("{\"success\":true,\"message\":\"Đã khóa tài khoản " + role + " #" + accountId + "\"}");
                    break;
                case "unlock":
                    lockDAO.unlockAccount(accountId);
                    extensionDAO.createNotification(accountId, null, "Tài khoản đã được mở khóa",
                            "Tài khoản " + role + " của bạn đã được mở khóa và có thể hoạt động trở lại.",
                            "ACCOUNT_UNLOCKED", "IN_APP");
                    out.print("{\"success\":true,\"message\":\"Đã mở khóa tài khoản " + role + " #" + accountId + "\"}");
                    break;
                default:
                    response.setStatus(404);
                    out.print("{\"success\":false,\"error\":\"Action không hợp lệ. Chỉ hỗ trợ lock, unlock\"}");
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"success\":false,\"error\":\"" + (e.getMessage() == null ? "" : e.getMessage().replace("\"", "'")) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"success\":false,\"error\":\"Lỗi server\"}");
        }
    }
}
