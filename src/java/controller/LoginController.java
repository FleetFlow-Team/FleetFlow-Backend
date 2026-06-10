package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import model.Account;
import utils.JwtUtils; // Đảm bảo import đúng

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/auth/login")
public class LoginController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("success", false);
        apiResponse.put("message", "HTTP method GET is not supported by this URL. Please use POST for login.");

        Gson gson = new Gson();
        response.getWriter().print(gson.toJson(apiResponse));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String email = request.getParameter("email");
            String password = request.getParameter("password");

            if (email != null && password != null) {
                AccountDAO dao = new AccountDAO();
                Account loginUser = dao.checkLogin(email.trim(), password.trim());

                if (loginUser != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("message", "Login successful");

                    // TRẢ THÔNG TIN USER NHƯ CŨ ĐANG CHẠY TỐT
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("email", loginUser.getEmail());
                    userData.put("fullName", loginUser.getFullName());
                    userData.put("roleName", loginUser.getRoleName());
                    apiResponse.put("user", userData);

                    // BỌC RIÊNG LUỒNG TOKEN: Nếu thiếu thư viện Jackson, Postman sẽ báo lỗi Token luôn chứ không sập 404
                    try {
                        String accessToken = JwtUtils.generateAccessToken(loginUser.getEmail(), loginUser.getRoleName());
                        String refreshToken = JwtUtils.generateRefreshToken(loginUser.getEmail());
                        
                        apiResponse.put("accessToken", accessToken);
                        apiResponse.put("refreshToken", refreshToken);
                    } catch (Throwable t) {
                        apiResponse.put("token_error", "Không thể tạo token do thiếu thư viện Jackson: " + t.getMessage());
                    }

                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Incorrect email or password");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Missing email or password");
            }
        } catch (Exception e) {
            log("Error at LoginController API: " + e.toString());
            apiResponse.put("success", false);
            apiResponse.put("message", "Internal Server Error: " + e.getMessage());
        } finally {
            // Giữ nguyên khối finally thần thánh giúp luôn luôn in ra kết quả JSON trên Postman
            String jsonResult = gson.toJson(apiResponse);
            out.print(jsonResult);
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