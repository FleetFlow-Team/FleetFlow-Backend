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

                    // Mẹo chống treo: Trả kết quả link test trực tiếp, khóa lệnh gửi mail thật nếu SMTP chưa bật
                    apiResponse.put("success", true);
                    apiResponse.put("message", "Token generated successfully.");
                    apiResponse.put("resetLinkForTest", resetLink);

                    // Khởi động gửi mail thật:
                    String subject = "[FleetFlow] Đặt Lại Mật Khẩu";
                    String content = "<p>Bấm vào link để đổi mật khẩu: <a href='" + resetLink + "'>Link</a></p>";
                    EmailUtils.sendEmail(email, subject, content);

                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Email not found.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Please enter email.");
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