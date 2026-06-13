package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
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

@WebServlet("/api/v1/account/delete")
@MultipartConfig
public class DeleteAccountController extends HttpServlet {

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
            String accountIdParam = request.getParameter("accountID");

            if (accountIdParam == null || accountIdParam.trim().isEmpty()) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu mã accountID cần xóa.");
                out.print(gson.toJson(apiResponse));
                return;
            }

            int accountId = Integer.parseInt(accountIdParam.trim());

            AccountDAO dao = new AccountDAO();
            boolean isSuccess = dao.deleteAccount(accountId);

            if (isSuccess) {
                apiResponse.put("success", true);
                apiResponse.put("message", "Đã xóa hoàn toàn tài khoản ID " + accountId + " và các dữ liệu liên quan khỏi hệ thống.");
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Không tìm thấy tài khoản với ID tương ứng hoặc xóa thất bại.");
            }

        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Mã accountID phải là ký tự số nguyên.");
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }
}