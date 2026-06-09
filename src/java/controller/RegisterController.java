package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import model.Account;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.EmailUtils;

@WebServlet("/api/v1/auth/register")
public class RegisterController extends HttpServlet {

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
            String password = request.getParameter("password");
            String fullName = request.getParameter("fullName");
            String phoneNumber = request.getParameter("phoneNumber");

            if (email != null) email = email.trim();
            if (password != null) password = password.trim();

            if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {
                AccountDAO dao = new AccountDAO();
                boolean isExist = dao.checkEmailExist(email);

                if (!isExist) {
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    Account newAcc = new Account("Customer", email, password, fullName, phoneNumber, "Active", now, now);
                    
                    try {
                        boolean isCreated = dao.registerAccount(newAcc);
                        if (isCreated) {
                            String subject = "[FleetFlow] Đăng Ký Tài Khoản Thành Công";
                            String content = "<h2>Chào mừng đến với FleetFlow!</h2><p>Tài khoản của bạn đã được khởi tạo thành công.</p>";
                            
                            boolean mailSent = EmailUtils.sendEmail(email, subject, content);

                            apiResponse.put("success", true);
                            apiResponse.put("message", mailSent ? "Account saved to DB and email sent!" : "Account saved to DB! (Mail delayed).");
                        } else {
                            apiResponse.put("success", false);
                            apiResponse.put("message", "Database rejected insertion.");
                        }
                    } catch (SQLException sqlEx) {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "SQL Error: " + sqlEx.getMessage());
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "This email is already taken.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Parameters are required.");
            }
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Exception: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }
}