/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.ExtensionDAO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.security.Key;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.ApiResponse;

/**
 * Ví / sổ công nợ của khách hàng.
 *
 * Controller mẫu dùng helper {@link utils.ApiResponse} để chuẩn hoá header + JSON
 * (khuôn {"success":..., "data"/"error":...}).
 */
@WebServlet("/api/v1/customer/wallet")
public class CustomerWalletController extends HttpServlet {

    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());
    private final ExtensionDAO dao = new ExtensionDAO();

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ApiResponse.prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Trả email nếu token hợp lệ và role = Customer. Nếu không hợp lệ, tự ghi
     * response lỗi (401/403) và trả null để caller dừng.
     */
    private String requireCustomer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null) {
            ApiResponse.error(response, 401, "Thiếu token xác thực");
            return null;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
            if (!"Customer".equalsIgnoreCase(claims.get("role").toString())) {
                ApiResponse.error(response, 403, "Không có quyền truy cập");
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            ApiResponse.error(response, 401, "Token không hợp lệ");
            return null;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ApiResponse.prepare(response);
        try {
            String email = requireCustomer(request, response);
            if (email == null) {
                return;
            }
            int customerId = dao.getCustomerIdByEmail(email);
            ApiResponse.success(response, dao.getWalletHistory(customerId));
        } catch (Exception e) {
            ApiResponse.error(response, 500, e.getMessage());
        }
    }
}
