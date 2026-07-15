package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.AccountDAO;
import dao.RefreshTokenDAO;
import model.Account;
import utils.JwtUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Endpoint đổi refreshToken (còn hạn 7 ngày) lấy accessToken mới (15 phút)
 * mà KHÔNG bắt user phải đăng nhập lại.
 *
 * Trước đây LoginController có generate refreshToken và trả về cho FE,
 * nhưng backend KHÔNG có endpoint nào nhận lại refreshToken để cấp
 * accessToken mới -> sau 15p access token hết hạn là user tự động bị văng
 * ra ngoài dù refreshToken vẫn còn hạn. Controller này bù vào chỗ thiếu đó.
 *
 * FE flow gợi ý:
 *  - Lưu refreshToken lúc login (localStorage / httpOnly cookie).
 *  - Khi 1 API bất kỳ trả 401 vì accessToken hết hạn -> gọi endpoint này
 *    để lấy accessToken mới -> retry lại request cũ.
 */
@WebServlet("/api/v1/auth/refresh")
public class RefreshTokenController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            // Nhận refreshToken từ body JSON: { "refreshToken": "..." }
            String refreshToken = extractRefreshToken(request);

            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu refreshToken");
                return;
            }

            // refreshToken hết hạn hoặc bị chỉnh sửa -> validateToken() sẽ fail
            if (!JwtUtils.validateToken(refreshToken)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                apiResponse.put("success", false);
                apiResponse.put("message", "refreshToken không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
                return;
            }

            String email = JwtUtils.getEmailFromToken(refreshToken);
            if (email == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                apiResponse.put("success", false);
                apiResponse.put("message", "refreshToken không hợp lệ. Vui lòng đăng nhập lại.");
                return;
            }

            // Chữ ký JWT hợp lệ chưa đủ — còn phải check bản ghi trong DB xem
            // token này đã bị REVOKE chưa (do logout / đổi mật khẩu / khóa tài
            // khoản / đã bị dùng để rotate trước đó). Đây chính là phần mà JWT
            // thuần (stateless) không tự làm được.
            RefreshTokenDAO refreshTokenDAO = new RefreshTokenDAO();
            int accountIdFromDb = refreshTokenDAO.getValidAccountIdByToken(refreshToken);
            if (accountIdFromDb == -1) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                apiResponse.put("success", false);
                apiResponse.put("message", "refreshToken đã bị thu hồi hoặc không tồn tại. Vui lòng đăng nhập lại.");
                return;
            }

            // refreshToken không mang claim "role" (xem JwtUtils.generateRefreshToken),
            // nên phải tra lại account trong DB để lấy role hiện tại + kiểm tra khóa.
            AccountDAO accountDAO = new AccountDAO();
            Account account = accountDAO.findByEmail(email);

            if (account == null || account.getId() != accountIdFromDb) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                apiResponse.put("success", false);
                apiResponse.put("message", "Tài khoản không tồn tại. Vui lòng đăng nhập lại.");
                return;
            }

            if ("LOCKED".equalsIgnoreCase(account.getStatus())) {
                // Tài khoản vừa bị khóa giữa lúc token còn hạn -> revoke luôn cho chắc.
                refreshTokenDAO.revokeAllTokensForAccount((int) account.getId());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                apiResponse.put("success", false);
                apiResponse.put("message", "Tài khoản đang bị tạm khóa.");
                return;
            }

            // Rotate: revoke refreshToken cũ (dùng 1 lần là hết) + cấp accessToken
            // và refreshToken mới. Giảm rủi ro nếu refreshToken cũ từng bị lộ.
            String newAccessToken = JwtUtils.generateAccessToken(account.getEmail(), account.getRoleName());
            String newRefreshToken = JwtUtils.generateRefreshToken(account.getEmail());

            refreshTokenDAO.revokeToken(refreshToken);
            java.util.Date expiresAt = new java.util.Date(
                    System.currentTimeMillis() + JwtUtils.REFRESH_TOKEN_EXPIRATION);
            refreshTokenDAO.saveToken((int) account.getId(), newRefreshToken, expiresAt);

            apiResponse.put("success", true);
            apiResponse.put("accessToken", newAccessToken);
            apiResponse.put("refreshToken", newRefreshToken);

        } catch (Exception e) {
            log("Error at RefreshTokenController API: " + e.toString());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            apiResponse.put("success", false);
            apiResponse.put("message", "Internal Server Error: " + e.getMessage());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    /**
     * Đọc refreshToken từ JSON body { "refreshToken": "..." }.
     * Fallback: nếu body rỗng, thử lấy từ header "Authorization: Bearer <refreshToken>".
     */
    private String extractRefreshToken(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        String body = sb.toString().trim();
        if (!body.isEmpty()) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("refreshToken") && !json.get("refreshToken").isJsonNull()) {
                    return json.get("refreshToken").getAsString();
                }
            } catch (Exception ignored) {
                // body không phải JSON hợp lệ -> rơi xuống fallback header bên dưới
            }
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}