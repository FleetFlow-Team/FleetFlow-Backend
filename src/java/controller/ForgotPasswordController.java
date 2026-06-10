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
                    // Tạo mật khẩu tạm ngẫu nhiên 8 ký tự
                    String temporaryPassword = UUID.randomUUID().toString().substring(0, 8);
                    
                    // Ghi đè vào cột PasswordHash cũ mà nhóm của bạn đã chốt cứng
                    boolean isUpdated = dao.updatePassword(email, temporaryPassword);

                    if (isUpdated) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Mật khẩu tạm thời đã gửi vào hòm thư.");
                        apiResponse.put("tempPasswordForTest", temporaryPassword); 

                        // Tiến hành lấy ID -> Bắn thư ngầm -> Lưu vết lịch sử bảng EmailLog
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