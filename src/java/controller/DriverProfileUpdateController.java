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

@WebServlet("/api/v1/driver/profile/update")
public class DriverProfileUpdateController extends HttpServlet {
//dc thay doi FullName, PhoneNumber, availabiltityStatus
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
            // Nhận dữ liệu chữ từ Body (x-www-form-urlencoded)
            String accountIdParam = request.getParameter("accountID");
            String fullName = request.getParameter("fullName");
            String phoneNumber = request.getParameter("phoneNumber");
            String availabilityStatus = request.getParameter("availabilityStatus");

            if (accountIdParam != null && !accountIdParam.trim().isEmpty()) {
                int accountId = Integer.parseInt(accountIdParam.trim());

                // Kiểm tra ràng buộc dữ liệu đầu vào cơ bản
                if (fullName == null || fullName.trim().isEmpty() || 
                    phoneNumber == null || phoneNumber.trim().isEmpty() || 
                    availabilityStatus == null || availabilityStatus.trim().isEmpty()) {
                    
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Vui lòng điền đầy đủ Full Name, Phone Number và Availability Status.");
                    out.print(gson.toJson(apiResponse));
                    return;
                }

                AccountDAO dao = new AccountDAO();
                boolean isSuccess = dao.updateDriverProfile(accountId, fullName.trim(), phoneNumber.trim(), availabilityStatus.trim());

                if (isSuccess) {
                    apiResponse.put("success", true);
                    apiResponse.put("message", "Cập nhật thông tin hồ sơ tài xế thành công!");
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy thông tin tài xế để cập nhật.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu tham số bắt buộc accountID.");
            }
        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Tham số accountID phải là ký tự số nguyên.");
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