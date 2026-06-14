package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import model.Account;
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
import utils.EmailUtils;
import utils.PasswordUtils;

@WebServlet("/api/v1/auth/change-password")
@MultipartConfig
public class ChangePasswordController extends HttpServlet {

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
            String oldPassword = request.getParameter("oldPassword");
            String newPassword = request.getParameter("newPassword");
            String confirmPassword = request.getParameter("confirmPassword");

            if (email != null) email = email.trim();
            if (oldPassword != null) oldPassword = oldPassword.trim();
            if (newPassword != null) newPassword = newPassword.trim();
            if (confirmPassword != null) confirmPassword = confirmPassword.trim();

            if (email != null && !email.isEmpty() 
                    && oldPassword != null && !oldPassword.isEmpty() 
                    && newPassword != null && !newPassword.isEmpty()) {

                if (newPassword.equals(confirmPassword)) {
                    
                    AccountDAO dao = new AccountDAO();
                    
                    // 1. Lấy chuỗi BCrypt hiện tại đang lưu dưới DB lên dựa vào Email
                    String dbHashedPassword = dao.getHashedPasswordByEmail(email);

                    // 2. 💡 ĐỐI CHIẾU BCRYPT: So sánh mật khẩu cũ nhập vào với mã hóa dưới DB
                    if (dbHashedPassword != null && PasswordUtils.checkPassword(oldPassword, dbHashedPassword)) {
                        
                        // 3. Nếu khớp -> Tiến hành băm mật khẩu MỚI và lưu đè xuống
                        String newHashedPassword = PasswordUtils.hashPassword(newPassword);
                        boolean isChanged = dao.updatePassword(email, newHashedPassword);

                        if (isChanged) {
                            apiResponse.put("success", true);
                            apiResponse.put("message", "Thay đổi mật khẩu thành công!");

                            // Bắn thư bảo mật chạy ngầm
                            int accountId = dao.getAccountIdByEmail(email);
                            String mailSubject = "[FleetFlow] Cảnh Báo Thay Đổi Mật Khẩu Tài Khoản";
                            String mailContent = EmailUtils.buildChangePasswordTemplate("Thành Viên FleetFlow", email);
                            
                            EmailUtils.sendEmailAndLogAsync(accountId, email, mailSubject, mailContent);
                        } else {
                            apiResponse.put("success", false);
                            apiResponse.put("message", "Lỗi cập nhật dữ liệu Database.");
                        }
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Mật khẩu cũ không chính xác.");
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Mật khẩu mới và xác nhận không khớp.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng cung cấp đầy đủ thông tin mật khẩu.");
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