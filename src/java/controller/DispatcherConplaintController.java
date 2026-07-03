package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ComplaintDAO;
import dao.NotificationDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

@WebServlet("/api/v1/dispatcher/complaints/*")
public class DispatcherConplaintController extends HttpServlet {

    private final ComplaintDAO dao = new ComplaintDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final Gson gson = new Gson();

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private boolean requireDispatcher(HttpServletRequest request, HttpServletResponse response,
            Map<String, Object> res) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
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
                String resolution = body.has("resolution") ? body.get("resolution").getAsString() : "";
                boolean ok = dao.resolveComplaint(complaintId, resolution);
                res.put("success", ok);
                if (ok) {
                    try {
                        int customerAccountId = dao.getCustomerAccountByComplaintId(complaintId);
                        if (customerAccountId != -1) {
                            notificationDAO.insert(customerAccountId, null,
                                    "Khiếu nại đã được xử lý",
                                    "Khiếu nại #" + complaintId + " của bạn đã được xử lý. Nội dung: " + resolution,
                                    "COMPLAINT_RESOLVED");
                        }
                    } catch (Exception notifyEx) {
                        System.err.println("Notify resolveComplaint loi: " + notifyEx.getMessage());
                    }
                }
            } else {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Path không hợp lệ. Dùng /{complaintId}/resolve");
            }
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
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Thiếu complaintId");
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