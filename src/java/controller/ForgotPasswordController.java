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
import utils.PasswordUtils;

@WebServlet("/api/v1/auth/forgot-password")
public class ForgotPasswordController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");

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
                    // 1. Tạo chuỗi mật khẩu tạm thời thô ngẫu nhiên
                    String temporaryPassword = UUID.randomUUID().toString().substring(0, 8);
                    
                    // 2. 💡 MÃ HÓA BCRYPT: Băm mật khẩu tạm này thành dạng $2a$10$... trước khi lưu DB
                    String hashedTempPassword = PasswordUtils.hashPassword(temporaryPassword);
                    boolean isUpdated = dao.updatePassword(email, hashedTempPassword);

                    if (isUpdated) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Mật khẩu tạm thời đã gửi vào hòm thư.");
                        apiResponse.put("tempPasswordForTest", temporaryPassword); // Giữ lại chuỗi thô để tiện test trên Postman

                        // 3. Gửi mã thô (temporaryPassword) vào email để người dùng có thể gõ đăng nhập
                        int accountId = dao.getAccountIdByEmail(email);
                        String mailSubject = "[FleetFlow] Cấp Lại Mật Khẩu Tạm Thời";
                        String mailContent = EmailUtils.buildForgotPasswordTemplate(temporaryPassword);
                        
                        EmailUtils.sendEmailAndLogAsync(accountId, email, mailSubject, mailContent);
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Lỗi hệ thống không thể đổi mật khẩu.");
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Email không tồn tại.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng nhập thông tin Email.");
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