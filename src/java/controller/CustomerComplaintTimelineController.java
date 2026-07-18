package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.ComplaintActionDAO;
import dao.ExtensionDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

/**
 * Khách xem timeline xử lý đơn của mình (spec mục 7):
 *
 *   GET /api/v1/customer/complaints/timeline?complaintId=123
 *
 * Nội dung timeline lấy nguyên văn từ ComplaintAction.CustomerMessage (hệ thống
 * tự sinh, dispatcher không gõ tay) — KHÔNG trả tên/thông tin dispatcher.
 * Dòng đầu tiên "Bạn đã gửi khiếu nại" tổng hợp từ Complaint.CreatedAt.
 */
@WebServlet("/api/v1/customer/complaints/timeline")
public class CustomerComplaintTimelineController extends HttpServlet {

    private final ComplaintActionDAO actionDAO = new ComplaintActionDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

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
        Map<String, Object> res = new HashMap<>();
        try {
            String header = request.getHeader("Authorization");
            String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
            if (token == null || !JwtUtils.validateToken(token)) {
                response.setStatus(401);
                res.put("success", false);
                res.put("message", "Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn");
                out.print(gson.toJson(res));
                return;
            }
            String role = JwtUtils.getRoleFromToken(token);
            if (role == null || !"Customer".equalsIgnoreCase(role)) {
                response.setStatus(403);
                res.put("success", false);
                res.put("message", "Chỉ tài khoản Customer được truy cập chức năng này");
                out.print(gson.toJson(res));
                return;
            }

            String idParam = request.getParameter("complaintId");
            if (idParam == null) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Thiếu tham số complaintId");
                out.print(gson.toJson(res));
                return;
            }
            int complaintId = Integer.parseInt(idParam);

            // Verify chủ đơn: đơn phải thuộc customer đang đăng nhập.
            // (Đơn của guest không có CustomerID -> không xem qua endpoint này;
            // kênh theo dõi cho guest là câu hỏi mở mục 11.2 của spec.)
            int customerId = extensionDAO.getCustomerIdByEmail(JwtUtils.getEmailFromToken(token));
            int ownerId = actionDAO.getOwnerCustomerId(complaintId);
            if (customerId == -1 || ownerId == -1 || ownerId != customerId) {
                response.setStatus(403);
                res.put("success", false);
                res.put("message", "Đơn không tồn tại hoặc không thuộc tài khoản của bạn");
                out.print(gson.toJson(res));
                return;
            }

            Map<String, Object> core = actionDAO.getComplaintCore(complaintId);
            List<Map<String, Object>> timeline = new ArrayList<>();
            Map<String, Object> first = new LinkedHashMap<>();
            first.put("time", core.get("createdAt"));
            first.put("actionCode", "SUBMITTED");
            first.put("message", "Bạn đã gửi khiếu nại");
            timeline.add(first);
            timeline.addAll(actionDAO.getTimelineForCustomer(complaintId));

            res.put("success", true);
            res.put("complaintId", complaintId);
            res.put("status", core.get("status"));
            res.put("timeline", timeline);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            res.put("success", false);
            res.put("message", "complaintId phải là số");
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        out.print(gson.toJson(res));
    }
}