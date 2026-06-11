package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import model.Account;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.EmailUtils;
import utils.PasswordUtils;

@WebServlet("/api/v1/auth/register")
public class RegisterController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // Sửa lỗi vỡ font tiếng Việt có dấu từ Postman
        request.setCharacterEncoding("UTF-8"); 

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String email = request.getParameter("email");
            String password = request.getParameter("password");
            String fullName = request.getParameter("fullName");
            String phoneNumber = request.getParameter("phoneNumber");
            String roleName = request.getParameter("roleName"); 

            if (email != null) email = email.trim();
            if (password != null) password = password.trim();
            if (fullName != null) fullName = fullName.trim();
            if (phoneNumber != null) phoneNumber = phoneNumber.trim();
            if (roleName != null) roleName = roleName.trim();

            if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {
                
                if (fullName == null || fullName.isEmpty()) {
                    fullName = email.split("@")[0];
                }
                
                // Chuẩn hóa Vai trò (Customer/Driver)
                if (roleName == null || roleName.isEmpty()) {
                    roleName = "Customer";
                } else {
                    roleName = roleName.substring(0, 1).toUpperCase() + roleName.substring(1).toLowerCase();
                    if (!roleName.equals("Customer") && !roleName.equals("Driver")) {
                        roleName = "Customer";
                    }
                }

                AccountDAO dao = new AccountDAO();
                boolean isExist = dao.checkEmailExist(email);

                if (!isExist) {
                    // 💡 TÍNH NĂNG MÃ HÓA BCRYPT: Băm mật khẩu thô thành dạng $2a$10$...
                    String dbHashedPassword = PasswordUtils.hashPassword(password);

                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    Account newAcc = new Account(roleName, email, dbHashedPassword, fullName, phoneNumber, "Active", now, now);
                    
                    boolean isCreated = dao.registerAccount(newAcc);
                    if (isCreated) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Đăng ký tài khoản thành công với vai trò " + roleName + "!");

                        // Thực thi luồng ngầm gửi thư cá nhân hóa + ghi EmailLog
                        int newAccountId = dao.getAccountIdByEmail(email);
                        String mailSubject = "[FleetFlow] Khởi Tạo Tài Khoản Thành Công";
                        String mailContent = EmailUtils.buildWelcomeTemplate(fullName, email, roleName);
                        
                        EmailUtils.sendEmailAndLogAsync(newAccountId, email, mailSubject, mailContent);
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Hệ thống DB từ chối lưu.");
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Email này đã được sử dụng.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng cung cấp Email và Mật khẩu đăng ký.");
            }
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }
}