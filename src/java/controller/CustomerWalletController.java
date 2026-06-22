/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.ExtensionDAO;
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
@WebServlet("/api/v1/customer/wallet")
public class CustomerWalletController extends HttpServlet {

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());
    private final ExtensionDAO dao = new ExtensionDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String requireCustomer(HttpServletRequest request, HttpServletResponse response, Map<String, Object> apiResponse) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null) {
            response.setStatus(401);
            apiResponse.put("success", false);
            return null;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
            if (!"Customer".equalsIgnoreCase(claims.get("role").toString())) {
                response.setStatus(403);
                apiResponse.put("success", false);
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            response.setStatus(401);
            apiResponse.put("success", false);
            return null;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        try {
            String email = requireCustomer(request, response, apiResponse);
            if (email == null) {
                out.print(gson.toJson(apiResponse));
                return;
            }
            int customerId = dao.getCustomerIdByEmail(email);
            apiResponse.put("success", true);
            apiResponse.put("data", dao.getWalletHistory(customerId));
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        out.print(gson.toJson(apiResponse));
    }
}
