package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.EmailUtils;

@WebServlet("/api/v1/auth/forgot-password")
public class ForgotPasswordController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String email = request.getParameter("email");
            if (email != null) email = email.trim();

            if (email != null && !email.isEmpty()) {
                AccountDAO dao = new AccountDAO();
                boolean isExist = dao.checkEmailExist(email);

                if (isExist) {
                    String resetToken = UUID.randomUUID().toString();
                    dao.saveResetToken(email, resetToken);

                    String resetLink = "http://127.0.0.1:5500/reset-password.html?token=" + resetToken;

                    // Chuẩn bị dữ liệu trả về ngay cho Frontend
                    apiResponse.put("success", true);
                    apiResponse.put("message", "Yêu cầu đặt lại mật khẩu đã được ghi nhận.");
                    apiResponse.put("resetLinkForTest", resetLink); // Tiện test dưới Console

                    // Bắn lệnh gửi mail chạy ngầm ở luồng khác, Servlet chạy tuột xuống dưới luôn không cần đợi
                    String subject = "[FleetFlow] Đặt Lại Mật Khẩu";
                    String content = "<h3>Yêu cầu thay đổi mật khẩu</h3>"
                            + "<p>Vui lòng click vào đường dẫn sau để tiến hành thay đổi mật khẩu của bạn:</p>"
                            + "<a href='" + resetLink + "' style='padding: 10px 20px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 5px;'>Đặt lại mật khẩu</a>"
                            + "<p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email.</p>";
                    
                    EmailUtils.sendEmailAsync(email, subject, content);

                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Email không tồn tại trong hệ thống.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng nhập Email.");
            }
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Internal Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }
}