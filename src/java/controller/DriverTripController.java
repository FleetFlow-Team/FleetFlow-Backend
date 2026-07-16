package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.DriverDAO;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import model.Account;
import service.TripTrackingService;
import utils.JwtUtils;

/**
 * Driver điều khiển lifecycle chuyến đi sau khi đã ACCEPT
 *
 * POST /api/v1/driver/trips/{bookingId}/start        — bắt đầu chuyến đi
 * POST /api/v1/driver/trips/{bookingId}/gps           — đẩy tọa độ GPS (mỗi 30s)
 * POST /api/v1/driver/trips/{bookingId}/complete      — hoàn thành chuyến đi
 *      (bắt buộc multipart/form-data, field ảnh "completionPhoto" — ảnh chụp
 *      xác nhận đã đến điểm trả khách, đồng thời dùng làm bằng chứng thu tiền
 *      mặt nếu khách trả CASH)
 *
 * Thanh toán CASH không còn action confirm-cash riêng — khách khai ý định trả
 * tiền mặt (qua FinalPaymentController) là tất toán ngay, tài xế có thể bấm
 * complete luôn sau khi nhận thông báo nhắc thu tiền.
 */
@WebServlet("/api/v1/driver/trips/*")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 15     // 15MB
)
public class DriverTripController extends HttpServlet {

    private static final String UPLOAD_DIR = "uploads/trip-completion";

    private final TripTrackingService tripService = new TripTrackingService();
    private final DriverDAO driverDAO = new DriverDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Account driverAcc = requireDriverAccount(request, response, out);
        if (driverAcc == null) {
            return;
        }

        String pathInfo = request.getPathInfo(); // "/{bookingId}/start|gps|complete"
        if (pathInfo == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu path\"}");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length != 3) {
            response.setStatus(400);
            out.print("{\"error\": \"Path không hợp lệ. Dùng /{bookingId}/start|gps|complete\"}");
            return;
        }

        int bookingId;
        try {
            bookingId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            out.print("{\"error\": \"bookingId phải là số nguyên\"}");
            return;
        }

        String action = parts[2];
        String ip = request.getRemoteAddr();

        try {
            int driverId = driverDAO.getDriverIdByAccountId((int) driverAcc.getId());
            if (driverId == -1) {
                response.setStatus(404);
                out.print("{\"error\": \"Không tìm thấy hồ sơ driver\"}");
                return;
            }

            switch (action) {
                case "start":
                    tripService.startTrip(bookingId, driverId, ip);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã bắt đầu chuyến đi\"}");
                    break;

                case "gps": {
                    JsonObject body = readJsonBody(request);
                    if (!body.has("latitude") || !body.has("longitude")) {
                        response.setStatus(400);
                        out.print("{\"error\": \"Thiếu latitude/longitude trong body\"}");
                        return;
                    }
                    BigDecimal lat = body.get("latitude").getAsBigDecimal();
                    BigDecimal lng = body.get("longitude").getAsBigDecimal();
                    tripService.pushGpsLocation(bookingId, driverId, lat, lng);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã ghi nhận vị trí\"}");
                    break;
                }

                case "complete": {
                    String photoUrl = savePhotoOrFail(request, response, out);
                    if (photoUrl == null) {
                        return; // lỗi đã ghi ra response bên trong savePhotoOrFail
                    }
                    tripService.completeTrip(bookingId, driverId, ip, photoUrl);
                    response.setStatus(200);
                    out.print("{\"success\": true, \"message\": \"Đã hoàn thành chuyến đi\", \"completionPhotoUrl\": \""
                            + esc(photoUrl) + "\"}");
                    break;
                }

                default:
                    response.setStatus(404);
                    out.print("{\"error\": \"Action không hợp lệ. Chỉ hỗ trợ start, gps, complete\"}");
            }

        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            out.print("{\"error\": \"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }

    // ===================== Helpers =====================

    /**
     * Đọc file ảnh "completionPhoto" từ multipart request, validate, lưu vào
     * thư mục uploads/trip-completion trong chính source (getRealPath — cùng
     * cơ chế với DriverController) để máy khác clone code vẫn thấy được ảnh.
     * Trả về null và tự ghi lỗi ra response nếu request không hợp lệ.
     */
    private String savePhotoOrFail(HttpServletRequest request, HttpServletResponse response, PrintWriter out)
            throws IOException, ServletException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
            response.setStatus(400);
            out.print("{\"error\": \"Phải gửi kèm ảnh xác nhận điểm đến để hoàn thành chuyến\"}");
            return null;
        }

        Part photoPart;
        try {
            photoPart = request.getPart("completionPhoto");
        } catch (Exception e) {
            response.setStatus(400);
            out.print("{\"error\": \"Không đọc được file ảnh: " + esc(e.getMessage()) + "\"}");
            return null;
        }

        if (photoPart == null || photoPart.getSize() == 0) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu file ảnh xác nhận điểm đến\"}");
            return null;
        }

        String uploadPath = utils.UploadUtils.resolveSourceWebDir(request) + File.separator
                + UPLOAD_DIR.replace("/", File.separator);
        System.out.println("[DEBUG][DriverTripController] uploadPath = " + uploadPath);
        ensureFolderExists(uploadPath);
        String savedRelativePath = saveFile(photoPart, "trip_complete_", uploadPath);
        System.out.println("[DEBUG][DriverTripController] Đã ghi file thật tại = "
                + uploadPath + File.separator + new File(savedRelativePath).getName());
        return savedRelativePath;
    }

    private void ensureFolderExists(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private String saveFile(Part part, String prefix, String uploadPath) throws IOException {
        String contentDisp = part.getHeader("content-disposition");
        String originalName = "photo.jpg";
        for (String token : contentDisp.split(";")) {
            if (token.trim().startsWith("filename")) {
                originalName = token.substring(token.indexOf("=") + 2, token.length() - 1);
                break;
            }
        }
        String uniqueName = prefix + UUID.randomUUID().toString() + "_" + originalName;
        part.write(uploadPath + File.separator + uniqueName);
        return UPLOAD_DIR + "/" + uniqueName;
    }

    private JsonObject readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private Account requireDriverAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn\"}");
            return null;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !role.equalsIgnoreCase("Driver")) {
            response.setStatus(403);
            out.print("{\"error\": \"Chỉ tài khoản Driver được truy cập chức năng này\"}");
            return null;
        }

        String email = JwtUtils.getEmailFromToken(token);
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

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}