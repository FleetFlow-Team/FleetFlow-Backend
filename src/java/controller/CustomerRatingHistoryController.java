/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.ExtensionDAO;
import dao.RatingDAO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Key;
import java.util.HashMap;
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
@WebServlet("/api/v1/customer/ratings")
public class CustomerRatingHistoryController extends HttpServlet {

    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());
 
    private final RatingDAO ratingDAO = new RatingDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
 
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
 
    private String requireCustomer(HttpServletRequest request, HttpServletResponse response, Map<String, Object> res) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null) {
            response.setStatus(401);
            res.put("success", false);
            res.put("message", "Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn");
            return null;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
            Object role = claims.get("role");
            if (role == null || !"Customer".equalsIgnoreCase(role.toString())) {
                response.setStatus(403);
                res.put("success", false);
                res.put("message", "Chỉ tài khoản Customer được truy cập chức năng này");
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            response.setStatus(401);
            res.put("success", false);
            res.put("message", "Token không hợp lệ hoặc đã hết hạn");
            return null;
        }
    }
 
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> res = new HashMap<>();
        try {
            String email = requireCustomer(request, response, res);
            if (email == null) {
                out.print(gson.toJson(res));
                return;
            }
            int customerId = extensionDAO.getCustomerIdByEmail(email);
            if (customerId == -1) {
                response.setStatus(404);
                res.put("success", false);
                res.put("message", "Không tìm thấy hồ sơ khách hàng");
                out.print(gson.toJson(res));
                return;
            }
            res.put("success", true);
            res.put("data", ratingDAO.getRatingsByCustomerId(customerId));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        out.print(gson.toJson(res));
    }
}
 