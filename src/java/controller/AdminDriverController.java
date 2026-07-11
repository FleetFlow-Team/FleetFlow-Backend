package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.DriverDAO;
import dao.ExtensionDAO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Key;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.Account;
import model.IdentityDocument;
import service.DriverVerificationService;

/**
 * Admin quản lý duyệt/từ chối hồ sơ tài xế
 *
 * BE-13: GET /api/v1/admin/drivers/pending — danh sách driver chờ duyệt BE-14:
 * POST /api/v1/admin/drivers/{accountId}/approve — duyệt hồ sơ BE-15: POST
 * /api/v1/admin/drivers/{accountId}/reject — từ chối hồ sơ
 *
 * Xác thực bằng JWT Bearer token (đồng bộ với CustomerController/AdminVehicleController),
 * KHÔNG dùng session.
 */
@WebServlet("/api/v1/admin/drivers/*")
public class AdminDriverController extends HttpServlet {

    private final DriverVerificationService service = new DriverVerificationService();
    private final DriverDAO driverDAO = new DriverDAO();
    private final AccountDAO accountDAO = new AccountDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();

    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());

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

    // BE-13: GET /api/v1/admin/drivers/pending
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account admin = getAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/pending" | "/all"

        // BE-mới: GET /api/v1/admin/drivers/all — danh sách toàn bộ driver kèm
        // Account.Status, dùng cho màn hình Admin khóa/mở khóa tài khoản driver.
        if ("/all".equals(pathInfo)) {
            try {
                List<Map<String, Object>> drivers = driverDAO.getAllDriversForAdmin();
                StringBuilder json = new StringBuilder();
                json.append("{\"success\": true, \"data\": [");
                for (int i = 0; i < drivers.size(); i++) {
                    json.append(mapToJson(drivers.get(i)));
                    if (i < drivers.size() - 1) json.append(",");
                }
                json.append("]}");
                response.setStatus(200);
                out.print(json.toString());
            } catch (Exception e) {
                response.setStatus(500);
                out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
            }
            return;
        }

        if (!"/pending".equals(pathInfo)) {
            response.setStatus(404);
            out.print("{\"error\": \"Endpoint không tồn tại\"}");
            return;
        }

        try {
            List<Account> drivers = service.getPendingDrivers();

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"data\": [");

            for (int i = 0; i < drivers.size(); i++) {
                Account acc = drivers.get(i);

                // TODO: tạm tắt lấy giấy tờ tài xế, mở lại khi tính năng duyệt hồ sơ hoạt động
                // List<IdentityDocument> docs = service.getDocsByAccountId((int) acc.getId());

                json.append("{");
                json.append("\"accountId\":").append(acc.getId()).append(",");
                json.append("\"fullName\":\"").append(esc(acc.getFullName())).append("\",");
                json.append("\"email\":\"").append(esc(acc.getEmail())).append("\",");
                json.append("\"phone\":\"").append(esc(acc.getPhoneNumber())).append("\",");
                json.append("\"createdAt\":\"").append(acc.getCreatedAt()).append("\",");
                json.append("\"documents\":[");

                // for (int j = 0; j < docs.size(); j++) {
                //     IdentityDocument doc = docs.get(j);
                //     json.append("{");
                //     json.append("\"docId\":").append(doc.getId()).append(",");
                //     json.append("\"docType\":\"").append(esc(doc.getDocType())).append("\",");
                //     json.append("\"fileUrl\":\"").append(esc(doc.getSecureFileUrl())).append("\",");
                //     json.append("\"status\":\"").append(esc(doc.getStatus())).append("\",");
                //     json.append("\"uploadedAt\":\"").append(doc.getUploadedAt()).append("\"");
                //     json.append("}");
                //     if (j < docs.size() - 1) {
                //         json.append(",");
                //     }
                // }

                json.append("]}");
                if (i < drivers.size() - 1) {
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

    // BE-14 + BE-15: POST /api/v1/admin/drivers/{accountId}/approve|reject
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(request, response);
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        Account adminAcc = getAdminAccount(request, response, out);
        if (adminAcc == null) {
            return;
        }
        int adminAccountId = (int) adminAcc.getId();

        String pathInfo = request.getPathInfo(); // "/{accountId}/approve" hoặc "/{accountId}/reject"

        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{accountId}/approve hoặc /{accountId}/reject\"}");
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

        String action = parts[2]; // "approve" hoặc "reject"

        try {
            switch (action) {
                case "approve":
                    service.approveDriver(accountId, adminAccountId);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Duyệt hồ sơ tài xế thành công\"}");
                    break;

                case "reject":
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = request.getReader();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }

                    String rejectReason = "";
                    if (sb.length() > 0) {
                        JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
                        if (body.has("rejectReason")) {
                            rejectReason = body.get("rejectReason").getAsString();
                        }
                    }

                    service.rejectDriver(accountId, rejectReason);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Từ chối hồ sơ tài xế thành công\"}");
                    break;

                case "lock":
                    requireRole(accountId, "Driver");
                    accountDAO.lockAccountById(accountId);
                    extensionDAO.createNotification(accountId, null,
                            "Tài khoản đã bị tạm khóa",
                            "Tài khoản tài xế của bạn đã bị Admin tạm khóa. Vui lòng liên hệ để được hỗ trợ mở lại.",
                            "ACCOUNT_LOCKED", "BOTH");
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã khóa tài khoản driver #" + accountId + "\"}");
                    break;

                case "unlock":
                    requireRole(accountId, "Driver");
                    accountDAO.unlockAccountById(accountId);
                    extensionDAO.createNotification(accountId, null,
                            "Tài khoản đã được mở khóa",
                            "Tài khoản tài xế của bạn đã được mở khóa. Bạn có thể tiếp tục nhận chuyến.",
                            "ACCOUNT_UNLOCKED", "BOTH");
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã mở khóa tài khoản driver #" + accountId + "\"}");
                    break;

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ approve, reject, lock, unlock\"}");
            }

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== JWT Helpers =====================

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private Account getAdminAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String token = extractToken(request);
        if (token == null) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập (thiếu Bearer token)\"}");
            return null;
        }

        String email;
        String role;
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(KEY).build()
                    .parseClaimsJws(token).getBody();
            email = claims.getSubject();
            Object roleObj = claims.get("role");
            role = roleObj == null ? null : roleObj.toString();
        } catch (Exception e) {
            response.setStatus(401);
            out.print("{\"error\": \"Token không hợp lệ hoặc đã hết hạn\"}");
            return null;
        }

        if (role == null || !role.equalsIgnoreCase("Admin")) {
            response.setStatus(403);
            out.print("{\"error\": \"Chỉ tài khoản Admin được truy cập chức năng này\"}");
            return null;
        }

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

    // Escape ký tự đặc biệt trong JSON string
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Chặn Admin lock/unlock nhầm 1 account không phải Driver (VD id đó là
     * Customer hoặc Dispatcher).
     */
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