/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import dao.RatingDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/marketing/email/trigger")
public class MarketingEmailController extends HttpServlet {

    private final RatingDAO dao = new RatingDAO();
    private final Gson gson = new Gson();
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> res = new HashMap<>();

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.equals("Bearer CRON_SECRET_KEY_123")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.put("success", false);
                res.put("message", "Không có quyền thực thi tác vụ này.");
                out.print(gson.toJson(res));
                return;
            }

            List<String> targetEmails = dao.getInactiveCustomerEmails(30);

            for (String email : targetEmails) {
                String voucherCode = "COMEBACK_" + System.currentTimeMillis();
                System.out.println(">>> Đã gửi email chứa voucher " + voucherCode + " đến: " + email);
            }

            res.put("success", true);
            res.put("message", "Đã quét và gửi thành công " + targetEmails.size() + " email marketing.");

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.put("success", false);
            res.put("message", "Lỗi hệ thống: " + e.getMessage());
        }
        
        out.print(gson.toJson(res));
    }
}