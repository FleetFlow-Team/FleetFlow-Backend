package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Account;
import model.IdentityDocument;
import service.DriverVerificationService;

/**
 * Admin quản lý duyệt/từ chối hồ sơ tài xế
 *
 * BE-13: GET  /api/v1/admin/drivers/pending          — danh sách driver chờ duyệt
 * BE-14: POST /api/v1/admin/drivers/{accountId}/approve — duyệt hồ sơ
 * BE-15: POST /api/v1/admin/drivers/{accountId}/reject  — từ chối hồ sơ
 */
@WebServlet("/api/v1/admin/drivers/*")
public class AdminDriverController extends HttpServlet {

    private final DriverVerificationService service = new DriverVerificationService();

    // BE-13: GET /api/v1/admin/drivers/pending
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String pathInfo = request.getPathInfo(); // "/pending"

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

                // Lấy giấy tờ của từng driver
                List<IdentityDocument> docs = service.getDocsByAccountId((int) acc.getId());

                json.append("{");
                json.append("\"accountId\":").append(acc.getId()).append(",");
                json.append("\"fullName\":\"").append(esc(acc.getFullName())).append("\",");
                json.append("\"email\":\"").append(esc(acc.getEmail())).append("\",");
                json.append("\"phone\":\"").append(esc(acc.getPhoneNumber())).append("\",");
                json.append("\"createdAt\":\"").append(acc.getCreatedAt()).append("\",");
                json.append("\"documents\":[");

                for (int j = 0; j < docs.size(); j++) {
                    IdentityDocument doc = docs.get(j);
                    json.append("{");
                    json.append("\"docId\":").append(doc.getId()).append(",");
                    json.append("\"docType\":\"").append(esc(doc.getDocType())).append("\",");
                    json.append("\"fileUrl\":\"").append(esc(doc.getSecureFileUrl())).append("\",");
                    json.append("\"status\":\"").append(esc(doc.getStatus())).append("\",");
                    json.append("\"uploadedAt\":\"").append(doc.getUploadedAt()).append("\"");
                    json.append("}");
                    if (j < docs.size() - 1) json.append(",");
                }

                json.append("]}");
                if (i < drivers.size() - 1) json.append(",");
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

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String pathInfo = request.getPathInfo(); // "/{accountId}/approve" hoặc "/{accountId}/reject"

        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        // Parse path: ["", "{accountId}", "approve"]
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

        // Lấy adminAccountId từ session
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("account") == null) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập\"}");
            return;
        }
        Account adminAcc = (Account) session.getAttribute("account");
        int adminAccountId = (int) adminAcc.getId();

        try {
            switch (action) {
                case "approve":
                    // BE-14
                    service.approveDriver(accountId, adminAccountId);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Duyệt hồ sơ tài xế thành công\"}");
                    break;

                case "reject":
                    // BE-15 — đọc rejectReason từ body JSON
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = request.getReader();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

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

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ approve hoặc reject\"}");
            }

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    // Escape ký tự đặc biệt trong JSON string
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}