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
import utils.PasswordUtils;

@WebServlet("/api/v1/auth/register")
@MultipartConfig
public class RegisterController extends HttpServlet {

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
            // Nhận các tham số chữ cơ bản
            String email = request.getParameter("email");
            String password = request.getParameter("password");
            String fullName = request.getParameter("fullName");
            String phoneNumber = request.getParameter("phoneNumber");
            String roleName = request.getParameter("roleName"); // "Customer" hoặc "Driver"
            String address = request.getParameter("address");   // Chỉ dùng riêng cho Customer nếu thích điền

            if (email != null) email = email.trim();
            if (password != null) password = password.trim();
            if (fullName != null) fullName = fullName.trim();
            if (phoneNumber != null) phoneNumber = phoneNumber.trim();
            if (roleName != null) roleName = roleName.trim();

            if (email == null || email.isEmpty() || password == null || password.isEmpty() || roleName == null || roleName.isEmpty()) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu thông tin bắt buộc: email, password, hoặc roleName.");
                out.print(gson.toJson(apiResponse));
                return;
            }

            // Kiểm tra giá trị hợp lệ của vai trò
            if (!roleName.equals("Customer") && !roleName.equals("Driver")) {
                apiResponse.put("success", false);
                apiResponse.put("message", "roleName không hợp lệ. Chỉ chấp nhận 'Customer' hoặc 'Driver'.");
                out.print(gson.toJson(apiResponse));
                return;
            }

            AccountDAO dao = new AccountDAO();
            boolean isExist = dao.checkEmailExist(email);

            if (!isExist) {
                // Mã hóa bảo mật mật khẩu
                String dbHashedPassword = PasswordUtils.hashPassword(password);
                if (fullName == null || fullName.isEmpty()) fullName = email.split("@")[0];

                Account newAcc = new Account(roleName, email, dbHashedPassword, fullName, phoneNumber, "Active", null, null);
                
                // Gọi hàm switch-case lồng Transaction tập trung tại DAO
                boolean isCreated = dao.registerUnifiedAccount(newAcc, address);

                if (isCreated) {
                    apiResponse.put("success", true);
                    apiResponse.put("message", "Đăng ký tài khoản vai trò [" + roleName + "] thành công!");
                    
                    // Lấy ra AccountID vừa sinh ra để trả về cho Frontend tiện điều hướng
                    int newId = dao.getAccountIdByEmail(email);
                    apiResponse.put("accountID", newId);
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Hệ thống DB từ chối lưu trữ dữ liệu.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Email này đã được đăng ký sử dụng trong hệ thống.");
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