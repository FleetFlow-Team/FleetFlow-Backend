package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.TripTrackingDAO;
import service.BookingExtensionService;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.TripGpsLog;
import utils.JwtUtils;

/**
 * Cho KHÁCH theo dõi vị trí xe của CHÍNH chuyến mình theo thời gian thực —
 * bổ sung tính năng an toàn: trước đây chỉ Dispatcher xem được vị trí xe trên
 * bản đồ tổng, khách ngồi trên xe (hoặc người thân ở nhà) không theo dõi được
 * lộ trình. Với dịch vụ có tài xế, khách cần thấy xe đang ở đâu để yên tâm và
 * để đối chiếu nếu xe đi sai tuyến.
 *
 *   GET /api/v1/customer/trips/{bookingId}/location
 *
 * Bảo mật:
 *  - Bắt buộc Bearer token Customer.
 *  - Chỉ trả vị trí nếu booking THUỘC VỀ chính khách này VÀ đang ONGOING
 *    (isOngoingBookingOfCustomer) — chống khách A dò vị trí xe chuyến khách B,
 *    và không lộ vị trí khi chuyến đã kết thúc.
 *  - FE gọi endpoint này theo chu kỳ (vd mỗi 15–30s) để cập nhật chấm xe.
 */
@WebServlet("/api/v1/customer/trips/*")
public class CustomerTripTrackingController extends HttpServlet {

    private final TripTrackingDAO trackingDAO = new TripTrackingDAO();
    private final BookingExtensionService extensionService = new BookingExtensionService();
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
            // 1) Auth: bắt buộc token Customer hợp lệ
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

            // 2) Lấy bookingId + action từ path: /{bookingId}/location hoặc /{bookingId}/overtime-preview
            String pathInfo = request.getPathInfo(); // "/54/location" | "/54/overtime-preview"
            boolean isLocation = pathInfo != null && pathInfo.endsWith("/location");
            boolean isOvertimePreview = pathInfo != null && pathInfo.endsWith("/overtime-preview");
            if (!isLocation && !isOvertimePreview) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "Path không hợp lệ. Dùng /{bookingId}/location hoặc /{bookingId}/overtime-preview");
                out.print(gson.toJson(res));
                return;
            }
            int bookingId;
            try {
                bookingId = Integer.parseInt(pathInfo.split("/")[1]);
            } catch (Exception e) {
                response.setStatus(400);
                res.put("success", false);
                res.put("message", "bookingId phải là số");
                out.print(gson.toJson(res));
                return;
            }

            // 3) Verify: booking thuộc chính khách này VÀ đang ONGOING
            String email = JwtUtils.getEmailFromToken(token);
            if (!trackingDAO.isOngoingBookingOfCustomer(bookingId, email)) {
                response.setStatus(403);
                res.put("success", false);
                res.put("message", "Chuyến đi không thuộc tài khoản của bạn hoặc không trong trạng thái đang di chuyển");
                out.print(gson.toJson(res));
                return;
            }

            // 4a) Tạm tính phí quá giờ — cho FE hiện lên bill nhảy dần
            if (isOvertimePreview) {
                Map<String, Object> preview = extensionService.previewOvertime(bookingId);
                res.put("success", true);
                res.putAll(preview);
                out.print(gson.toJson(res));
                return;
            }

            // 4b) Vị trí GPS mới nhất
            TripGpsLog latest = trackingDAO.getLatestGpsLog(bookingId);
            if (latest == null) {
                // Đang ONGOING nhưng chưa có điểm GPS nào (tài xế chưa đẩy vị trí)
                res.put("success", true);
                res.put("bookingId", bookingId);
                res.put("hasLocation", false);
                res.put("message", "Chưa có dữ liệu vị trí — tài xế chưa gửi định vị");
                out.print(gson.toJson(res));
                return;
            }

            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("latitude", latest.getLatitude());
            loc.put("longitude", latest.getLongitude());
            loc.put("recordedAt", latest.getRecordedAt() != null ? latest.getRecordedAt().toString() : null);

            res.put("success", true);
            res.put("bookingId", bookingId);
            res.put("hasLocation", true);
            res.put("location", loc);
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        out.print(gson.toJson(res));
    }
}