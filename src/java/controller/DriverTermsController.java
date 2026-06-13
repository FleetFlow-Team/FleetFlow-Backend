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

@WebServlet("/api/v1/driver/terms/accept") // 🚀 Khớp định tuyến cấu hình URL
public class DriverTermsController extends HttpServlet {

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
            // Nhận accountID từ body request dữ liệu chữ thô
            String accountIdParam = request.getParameter("accountID");

            if (accountIdParam != null && !accountIdParam.trim().isEmpty()) {
                int accountId = Integer.parseInt(accountIdParam.trim());

                AccountDAO dao = new AccountDAO();
                boolean isSuccess = dao.acceptDriverTerms(accountId);

                if (isSuccess) {
                    apiResponse.put("success", true);
                    apiResponse.put("message", "Tài xế đã xác nhận đồng ý điều khoản dịch vụ thành công!");
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy tài xế tương ứng hoặc cập nhật thất bại.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu tham số bắt buộc accountID.");
            }
        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Định dạng accountID phải là kiểu số nguyên.");
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