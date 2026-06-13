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

@WebServlet("/api/v1/driver/profile") // 🚀 Đúng chuẩn url của bạn
public class DriverProfileController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // Nhận tham số từ chuỗi Query Parameter trên URL (?accountID=xx)
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String accountIdParam = request.getParameter("accountID");

            if (accountIdParam != null && !accountIdParam.trim().isEmpty()) {
                int accountId = Integer.parseInt(accountIdParam.trim());

                AccountDAO dao = new AccountDAO();
                Map<String, Object> driverData = dao.getDriverProfile(accountId);

                if (driverData != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", driverData);
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy hồ sơ tài xế hoặc tài khoản không phải vai trò Driver.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu tham số mã tài khoản (accountID) trên URL.");
            }
        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Mã accountID truyền lên URL phải là ký tự số.");
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }
}