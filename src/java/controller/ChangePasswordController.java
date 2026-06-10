package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.EmailUtils;

@WebServlet("/api/v1/auth/change-password")
public class ChangePasswordController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // Chặn lỗi vỡ font tiếng Việt
        request.setCharacterEncoding("UTF-8");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            // Lấy dữ liệu truyền lên từ client
            String email = request.getParameter("email");
            String oldPassword = request.getParameter("oldPassword");
            String newPassword = request.getParameter("newPassword");
            String confirmPassword = request.getParameter("confirmPassword");

            if (email != null) email = email.trim();
            if (oldPassword != null) oldPassword = oldPassword.trim();
            if (newPassword != null) newPassword = newPassword.trim();
            if (confirmPassword != null) confirmPassword = confirmPassword.trim();

            // Validate đầu vào dữ liệu cơ bản
            if (email != null && !email.isEmpty() 
                    && oldPassword != null && !oldPassword.isEmpty() 
                    && newPassword != null && !newPassword.isEmpty()) {

                // 1. Kiểm tra mật khẩu mới có trùng khớp với confirm mật khẩu không
                if (newPassword.equals(confirmPassword)) {
                    
                    AccountDAO dao = new AccountDAO();
                    
                    // 2. Gọi xuống DB thực thi lệnh thay đổi mật khẩu
                    boolean isChanged = dao.changePassword(email, oldPassword, newPassword);

                    if (isChanged) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Thay đổi mật khẩu thành công!");

                        // 3. Tự động lấy thông tin để bắn mail bảo mật luồng ngầm + Lưu vết EmailLog
                        int accountId = dao.getAccountIdByEmail(email);
                        String mailSubject = "[FleetFlow] Cảnh Báo Thay Đổi Mật Khẩu Tài Khoản";
                        
                        // Để lấy fullName tạm thời tạo phom mail, ta trích xuất từ hàm getAccountId
                        String mailContent = EmailUtils.buildChangePasswordTemplate("Thành Viên FleetFlow", email);
                        
                        // Kích hoạt luồng gửi mail ngầm (CampaignID lưu số 1 tương ứng quy ước mail hệ thống)
                        EmailUtils.sendEmailAndLogAsync(accountId, email, mailSubject, mailContent);

                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Mật khẩu cũ không chính xác hoặc tài khoản không tồn tại.");
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Mật khẩu mới và mật khẩu xác nhận không trùng khớp.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng nhập đầy đủ: Email, Mật khẩu cũ và Mật khẩu mới.");
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