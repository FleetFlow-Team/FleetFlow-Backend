package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.LandmarkDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * API quản lý danh mục Landmark (bến xe, sân bay...) dành cho Admin/Dispatcher.
 * Theo đúng pattern của HolidayController — cùng team đang dùng cho các danh
 * mục tham chiếu (reference data) tương tự.
 *
 * GET    /api/v1/admin/landmarks           -> danh sách tất cả (kể cả đã ẩn)
 * POST   /api/v1/admin/landmarks           -> thêm landmark mới
 * PUT    /api/v1/admin/landmarks/{id}      -> sửa thông tin
 * DELETE /api/v1/admin/landmarks/{id}      -> ẩn (soft delete)
 * PUT    /api/v1/admin/landmarks/{id}/restore -> hiện lại landmark đã ẩn
 */
@WebServlet("/api/v1/admin/landmarks/*")
public class AdminLandmarkController extends HttpServlet {

    private final LandmarkDAO dao = new LandmarkDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();
        try {
            res.put("success", true);
            res.put("data", dao.getAllLandmarks());
            response.getWriter().print(gson.toJson(res));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
            response.getWriter().print(gson.toJson(res));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();

        try {
            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();

            String name = body.get("name").getAsString();
            String address = body.get("address").getAsString();
            BigDecimal lat = body.get("lat").getAsBigDecimal();
            BigDecimal lng = body.get("lng").getAsBigDecimal();
            String category = body.has("category") ? body.get("category").getAsString() : "OTHER";

            // TODO: lấy createdBy thật từ JWT/session của người đang đăng nhập
            // (repo hiện các controller admin khác — VD HolidayController — cũng
            // chưa gắn kiểm tra JWT ở tầng controller, nên tạm để 0 cho đồng bộ,
            // sẽ nối vào chung 1 lượt khi team chuẩn hoá auth cho khối admin).
            int createdBy = 0;

            int newId = dao.createLandmark(name, address, lat, lng, category, createdBy);
            res.put("success", newId > 0);
            res.put("landmarkId", newId);
        } catch (Exception e) {
            response.setStatus(400);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Thiếu landmarkId trên URL.");
                response.getWriter().print(gson.toJson(res));
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            int landmarkId = Integer.parseInt(parts[0]);

            // PUT /api/v1/admin/landmarks/{id}/restore -> hiện lại landmark đã ẩn
            if (parts.length > 1 && "restore".equalsIgnoreCase(parts[1])) {
                res.put("success", dao.restoreLandmark(landmarkId));
                response.getWriter().print(gson.toJson(res));
                return;
            }

            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            String name = body.has("name") ? body.get("name").getAsString() : null;
            String address = body.has("address") ? body.get("address").getAsString() : null;
            BigDecimal lat = body.has("lat") ? body.get("lat").getAsBigDecimal() : null;
            BigDecimal lng = body.has("lng") ? body.get("lng").getAsBigDecimal() : null;
            String category = body.has("category") ? body.get("category").getAsString() : null;

            res.put("success", dao.updateLandmark(landmarkId, name, address, lat, lng, category));
        } catch (Exception e) {
            response.setStatus(400);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        String pathInfo = request.getPathInfo();
        Map<String, Object> res = new HashMap<>();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Thiếu landmarkId trên URL.");
                response.getWriter().print(gson.toJson(res));
                return;
            }
            res.put("success", dao.deleteLandmark(Integer.parseInt(pathInfo.substring(1))));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }
}