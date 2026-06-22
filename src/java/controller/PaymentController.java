/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ExtensionDAO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.BufferedReader;
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
@WebServlet("/api/v1/payments/momo/create")
public class PaymentController extends HttpServlet {

    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());
    private final ExtensionDAO dao = new ExtensionDAO();
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
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        request.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        try {
            if (requireCustomer(request, response, apiResponse) == null) {
                out.print(gson.toJson(apiResponse));
                return;
            }
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            int invoiceId = body.get("invoiceId").getAsInt();
            String paymentType = body.has("paymentType") ? body.get("paymentType").getAsString() : "FINAL";
            int paymentId = dao.createPayment(invoiceId, paymentType, body.get("amount").getAsBigDecimal());
            apiResponse.put("success", true);
            apiResponse.put("paymentUrl", "https://test-payment.momo.vn/v2/gateway/api/create?orderId=" + paymentId);
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        out.print(gson.toJson(apiResponse));
    }
}
