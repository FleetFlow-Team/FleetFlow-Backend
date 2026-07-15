package controller;

import com.google.gson.Gson;
import dao.LandmarkDAO;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * API public cho khách xem danh sách "điểm đến cố định" (Landmark) khi đặt
 * xe — Bến xe Miền Tây, Miền Đông, sân bay TSN, Nội Bài, Vũng Tàu... Khách
 * chọn 1 landmark trong danh sách, FE tự điền lat/lng vào form, rồi gọi
 * POST /api/v1/bookings với bookingType = DISTANCE như bình thường (không
 * phân loại nội/liên tỉnh — xem lý do trong Landmark.java).
 *
 * GET /api/v1/landmarks -> danh sách landmark đang hoạt động
 */
@WebServlet("/api/v1/landmarks")
public class LandmarkController extends HttpServlet {

    private final LandmarkDAO dao = new LandmarkDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();
        try {
            res.put("success", true);
            res.put("data", dao.getActiveLandmarks());
            response.getWriter().print(gson.toJson(res));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
            response.getWriter().print(gson.toJson(res));
        }
    }
}