/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ComplaintDAO;
import dao.ComplaintDAO.ComplaintForm;
import dao.ExtensionDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import service.ComplaintWorkflowService;
import utils.JwtUtils;

/**
 * Khách gửi khiếu nại — luồng rút gọn theo quyết định của PO so với
 * Report_Flow_Complaint.md gốc:
 *
 *   - Chỉ 2 loại: LOST_LUGGAGE, OTHER (OTHER = khách tự mô tả tự do, không
 *     còn lớp phân loại issueType).
 *   - Bắt buộc đăng nhập, khiếu nại luôn gắn với 1 booking ĐÃ HOÀN THÀNH của
 *     chính khách đó (không còn khách vãng lai) — khách thao tác từ màn hình
 *     lịch sử chuyến đi đã hoàn thành.
 *   - Gửi thành công -> báo ngay cho Admin + Dispatcher để tiếp nhận.
 */
@WebServlet("/api/v1/complaints")
public class CustomerComplaintController extends HttpServlet {

    private final ComplaintDAO dao = new ComplaintDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();
    private final ComplaintWorkflowService workflow = new ComplaintWorkflowService();
    private final Gson gson = new Gson();

    private static final int MAX_COMPLAINTS_PER_BOOKING = 1;

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private Integer requireCustomerId(HttpServletRequest request, HttpServletResponse response,
            Map<String, Object> res) throws Exception {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            res.put("success", false);
            res.put("message", "Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn");
            return null;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !"Customer".equalsIgnoreCase(role)) {
            response.setStatus(403);
            res.put("success", false);
            res.put("message", "Chỉ tài khoản Customer được gửi khiếu nại");
            return null;
        }
        int customerId = extensionDAO.getCustomerIdByEmail(JwtUtils.getEmailFromToken(token));
        if (customerId == -1) {
            response.setStatus(404);
            res.put("success", false);
            res.put("message", "Không tìm thấy hồ sơ khách hàng");
            return null;
        }
        return customerId;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        prepare(response);
        Map<String, Object> res = new HashMap<>();
        try {
            Integer customerId = requireCustomerId(request, response, res);
            if (customerId == null) {
                response.getWriter().print(gson.toJson(res));
                return;
            }

            JsonObject body = readBody(request);

            String type = getStr(body, "type");
            if (type == null) {
                fail(response, res, 400, "Thiếu 'type'. Chấp nhận: LOST_LUGGAGE, OTHER.");
                return;
            }
            type = type.toUpperCase();
            if (!type.equals("LOST_LUGGAGE") && !type.equals("OTHER")) {
                fail(response, res, 400, "type không hợp lệ. Chấp nhận: LOST_LUGGAGE, OTHER.");
                return;
            }

            if (!body.has("bookingId") || body.get("bookingId").isJsonNull()) {
                fail(response, res, 400, "Thiếu 'bookingId'. Khiếu nại phải gắn với 1 chuyến đi đã hoàn thành.");
                return;
            }
            int bookingId;
            try {
                bookingId = body.get("bookingId").getAsInt();
            } catch (Exception e) {
                fail(response, res, 400, "bookingId không hợp lệ.");
                return;
            }

            if (!dao.isBookingOwnedByAndCompleted(bookingId, customerId)) {
                fail(response, res, 400, "Chuyến đi không tồn tại, chưa hoàn thành, hoặc không thuộc tài khoản của bạn.");
                return;
            }

            if (dao.countComplaintsByBooking(bookingId) >= MAX_COMPLAINTS_PER_BOOKING) {
                fail(response, res, 429, "Bạn đã gửi khiếu nại đủ số lần cho phép cho chuyến đi này (tối đa "
                        + MAX_COMPLAINTS_PER_BOOKING + " lần).");
                return;
            }

            String content = getStr(body, "content");
            if (content == null) {
                fail(response, res, 400, "Cần nhập nội dung mô tả (content).");
                return;
            }

            ComplaintForm f = new ComplaintForm();
            f.type = type;
            f.content = content;
            f.bookingId = bookingId;
            f.customerId = customerId;

            int complaintId = dao.createComplaint(f);
            if (complaintId <= 0) {
                fail(response, res, 500, "Không thể lưu khiếu nại. Vui lòng thử lại.");
                return;
            }

            workflow.notifyStaffNewComplaint(complaintId, type);

            res.put("success", true);
            res.put("complaintId", complaintId);
            res.put("message", "Đã gửi khiếu nại thành công. Chúng tôi sẽ xử lý sớm.");
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    private void fail(HttpServletResponse response, Map<String, Object> res, int status, String message)
            throws IOException {
        response.setStatus(status);
        res.put("success", false);
        res.put("message", message);
        response.getWriter().print(gson.toJson(res));
    }

    private JsonObject readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private String getStr(JsonObject body, String key) {
        if (body.has(key) && !body.get(key).isJsonNull()) {
            String v = body.get(key).getAsString().trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }
}
