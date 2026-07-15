package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.RefreshTokenDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/api/v1/auth/logout")
@MultipartConfig
public class LogoutController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate(); 
            }

            // Revoke refreshToken trong DB — đây là bước quan trọng nhất của logout
            // "chuyên nghiệp": nếu chỉ invalidate session mà không revoke refreshToken,
            // token đó vẫn dùng được để refresh accessToken mới suốt 7 ngày còn lại.
            String refreshToken = extractRefreshToken(request);
            if (refreshToken != null && !refreshToken.trim().isEmpty()) {
                new RefreshTokenDAO().revokeToken(refreshToken);
            }

            apiResponse.put("success", true);
            apiResponse.put("message", "Logout successful. Please remove tokens from client-side storage.");
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Error: " + e.getMessage());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    /**
     * Đọc refreshToken từ JSON body { "refreshToken": "..." }.
     * Nếu FE không gửi kèm (client cũ chưa update) thì bỏ qua bước revoke,
     * chỉ invalidate session như trước — không làm fail cả request logout.
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
        if (body.isEmpty()) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("refreshToken") && !json.get("refreshToken").isJsonNull()) {
                return json.get("refreshToken").getAsString();
            }
        } catch (Exception ignored) {
            // body không phải JSON hợp lệ -> bỏ qua, không revoke được thì thôi
        }
        return null;
    }
}   