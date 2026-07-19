package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.ComplaintDAO;
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
 * Endpoint dispatcher xử lý khiếu nại (2 loại: LOST_LUGGAGE, OTHER — bản rút
 * gọn theo quyết định của PO so với Report_Flow_Complaint.md gốc):
 *
 *   GET    /api/v1/dispatcher/complaints                          — danh sách đơn
 *   POST   /api/v1/dispatcher/complaints/{id}/assign              — nhận xử lý (PENDING -> IN_PROGRESS)
 *   POST   /api/v1/dispatcher/complaints/{id}/actions/contact-driver
 *          body { "result": "HAS_ITEM" | "NO_ITEM" | "NO_RESPONSE" }   (chỉ LOST_LUGGAGE)
 *   POST   /api/v1/dispatcher/complaints/{id}/actions/handle
 *          body { "action": "VERIFIED_HANDLED" | "CANNOT_VERIFY" | "ESCALATED" | "REJECTED" }   (chỉ OTHER)
 *   PUT    /api/v1/dispatcher/complaints/{id}/tag
 *          body { "issueType": "VEHICLE_VIOLATION" | "APP_ISSUE" | "BILLING_DISPUTE" |
 *                 "STAFF_ATTITUDE" | "SAFETY_CONCERN" | "OTHER_UNCATEGORIZED" }   (chỉ OTHER, bắt buộc trước /actions/handle)
 *   PUT    /api/v1/dispatcher/complaints/{id}/resolve
 *          body { "outcome": "RESOLVED" | "CLOSED_UNRESOLVED", "reason_code"?: "..." }
 *   DELETE /api/v1/dispatcher/complaints/{id}                     — ẩn đơn (soft delete = cờ hidden)
 */
@WebServlet("/api/v1/dispatcher/complaints/*")
public class DispatcherConplaintController extends HttpServlet {

    private final ComplaintDAO dao = new ComplaintDAO();
    private final ComplaintWorkflowService workflow = new ComplaintWorkflowService();
    private final AccountDAO accountDAO = new AccountDAO();
    private final Gson gson = new Gson();

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
    }

    private boolean requireDispatcher(HttpServletRequest request, HttpServletResponse response,
            Map<String, Object> res) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            res.put("success", false);
            res.put("message", "Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn");
            return false;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("Dispatcher") || role.equalsIgnoreCase("Admin"))) {
            response.setStatus(403);
            res.put("success", false);
            res.put("message", "Chỉ Dispatcher hoặc Admin được truy cập chức năng này");
            return false;
        }
        return true;
    }

    /** AccountID của dispatcher đang thao tác — để ghi ComplaintAction + AuditLog. */
    private int currentAccountId(HttpServletRequest request) throws Exception {
        String email = JwtUtils.getEmailFromToken(extractToken(request));
        return accountDAO.getAccountIdByEmail(email);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        Map<String, Object> res = new HashMap<>();
        try {
            if (!requireDispatcher(request, response, res)) {
                response.getWriter().print(gson.toJson(res));
                return;
            }
            res.put("success", true);
            res.put("data", dao.getComplaints());
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        prepare(response);
        Map<String, Object> res = new HashMap<>();
        try {
            if (!requireDispatcher(request, response, res)) {
                response.getWriter().print(gson.toJson(res));
                return;
            }
            String pathInfo = request.getPathInfo();
            if (pathInfo == null) {
                badRequest(response, res, "Path không hợp lệ. Dùng /{id}/assign, /{id}/actions/contact-driver hoặc /{id}/actions/handle");
            } else if (pathInfo.endsWith("/assign")) {
                int complaintId = Integer.parseInt(pathInfo.split("/")[1]);
                workflow.assign(complaintId, currentAccountId(request), request.getRemoteAddr());
                res.put("success", true);
                res.put("message", "Đã nhận xử lý đơn #" + complaintId);
            } else if (pathInfo.endsWith("/actions/contact-driver")) {
                int complaintId = Integer.parseInt(pathInfo.split("/")[1]);
                JsonObject body = readBody(request);
                String result = body.has("result") ? body.get("result").getAsString() : null;
                String msg = workflow.recordContactDriver(complaintId, result,
                        currentAccountId(request), request.getRemoteAddr());
                res.put("success", true);
                res.put("customerMessage", msg);
            } else if (pathInfo.endsWith("/actions/handle")) {
                int complaintId = Integer.parseInt(pathInfo.split("/")[1]);
                JsonObject body = readBody(request);
                String action = body.has("action") ? body.get("action").getAsString() : null;
                String msg = workflow.handle(complaintId, action,
                        currentAccountId(request), request.getRemoteAddr());
                res.put("success", true);
                res.put("customerMessage", msg);
            } else {
                badRequest(response, res, "Path không hợp lệ. Dùng /{id}/assign, /{id}/actions/contact-driver hoặc /{id}/actions/handle");
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            res.put("success", false);
            res.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            response.setStatus(409);
            res.put("success", false);
            res.put("message", e.getMessage());
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        prepare(response);
        Map<String, Object> res = new HashMap<>();
        try {
            if (!requireDispatcher(request, response, res)) {
                response.getWriter().print(gson.toJson(res));
                return;
            }
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.endsWith("/resolve")) {
                int complaintId = Integer.parseInt(pathInfo.split("/")[1]);
                JsonObject body = readBody(request);
                String outcome = body.has("outcome") ? body.get("outcome").getAsString() : null;
                String reasonCode = body.has("reason_code") ? body.get("reason_code").getAsString() : null;
                String msg = workflow.resolve(complaintId, outcome, reasonCode,
                        currentAccountId(request), request.getRemoteAddr());
                res.put("success", true);
                res.put("customerMessage", msg);
            } else if (pathInfo != null && pathInfo.endsWith("/tag")) {
                int complaintId = Integer.parseInt(pathInfo.split("/")[1]);
                JsonObject body = readBody(request);
                String issueType = body.has("issueType") ? body.get("issueType").getAsString() : null;
                workflow.tag(complaintId, issueType, currentAccountId(request), request.getRemoteAddr());
                res.put("success", true);
                res.put("message", "Đã gắn nhãn issueType cho đơn #" + complaintId);
            } else {
                badRequest(response, res, "Path không hợp lệ. Dùng /{complaintId}/resolve hoặc /{complaintId}/tag");
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            res.put("success", false);
            res.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            response.setStatus(409);
            res.put("success", false);
            res.put("message", e.getMessage());
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        Map<String, Object> res = new HashMap<>();
        try {
            if (!requireDispatcher(request, response, res)) {
                response.getWriter().print(gson.toJson(res));
                return;
            }
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                badRequest(response, res, "Thiếu complaintId");
            } else {
                int complaintId = Integer.parseInt(pathInfo.substring(1).split("/")[0]);
                res.put("success", dao.softDeleteComplaint(complaintId));
            }
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    private void badRequest(HttpServletResponse response, Map<String, Object> res, String message) {
        response.setStatus(400);
        res.put("success", false);
        res.put("message", message);
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
}