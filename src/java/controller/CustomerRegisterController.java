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

@WebServlet("/api/v1/customer/register")
public class CustomerRegisterController extends HttpServlet {

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
            String password = request.getParameter("password");
            String fullName = request.getParameter("fullName");
            String phoneNumber = request.getParameter("phoneNumber");

            if (email != null) {
                email = email.trim();
            }
            if (password != null) {
                password = password.trim();
            }
            if (fullName != null) {
                fullName = fullName.trim();
            }
            if (phoneNumber != null) {
                phoneNumber = phoneNumber.trim();
            }

            if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {

                if (fullName == null || fullName.isEmpty()) {
                    fullName = email.split("@")[0];
                }

                AccountDAO dao = new AccountDAO();
                boolean isExist = dao.checkEmailExist(email);

                if (!isExist) {
                    // Mã hóa mật khẩu bảo mật bằng BCrypt trước khi lưu
                    String dbHashedPassword = PasswordUtils.hashPassword(password);
                    Timestamp now = new Timestamp(System.currentTimeMillis());

                    Account newAcc = new Account("Customer", email, dbHashedPassword, fullName, phoneNumber, "Active", now, now);

                    boolean isCreated = dao.registerAccount(newAcc);
                    if (isCreated) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Đăng ký tài khoản Khách hàng thành công!");

                        // Gửi email chào mừng chạy ngầm (Async)
                        int newAccountId = dao.getAccountIdByEmail(email);
                        String mailSubject = "[FleetFlow] Khởi Tạo Tài Khoản Thành Công";
                        String mailContent = EmailUtils.buildWelcomeTemplate(fullName, email, "Customer");
                        EmailUtils.sendEmailAndLogAsync(newAccountId, email, mailSubject, mailContent);
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Hệ thống DB từ chối lưu dữ liệu.");
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

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
