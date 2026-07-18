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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JwtUtils;

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/admin/vouchers/*")
public class AdminVoucherController extends HttpServlet {

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
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String requireAdmin(HttpServletRequest request, HttpServletResponse response, Map<String, Object> apiResponse) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            apiResponse.put("success", false);
            apiResponse.put("message", "Unauthorized");
            return null;
        }
        if (!"Admin".equalsIgnoreCase(JwtUtils.getRoleFromToken(token))) {
            response.setStatus(403);
            apiResponse.put("success", false);
            apiResponse.put("message", "Forbidden");
            return null;
        }
        return JwtUtils.getEmailFromToken(token);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        try {
            if (requireAdmin(request, response, apiResponse) == null) {
                out.print(gson.toJson(apiResponse));
                return;
            }
            String path = request.getPathInfo();
            if (path == null || path.equals("/")) {
                String status = request.getParameter("status");
                apiResponse.put("success", true);
                apiResponse.put("data", dao.getVouchers(status));
            } else {
                int id = Integer.parseInt(path.split("/")[1]);
                Map<String, Object> v = dao.getVoucherById(id);
                if (v != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", v);
                } else {
                    response.setStatus(404);
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Not found");
                }
            }
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        out.print(gson.toJson(apiResponse));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        request.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        try {
            String email = requireAdmin(request, response, apiResponse);
            if (email == null) {
                out.print(gson.toJson(apiResponse));
                return;
            }
            int adminId = dao.getAccountIdByEmail(email);
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            dao.createVoucher(
                body.get("code").getAsString(),
                body.get("discountType").getAsString(),
                body.get("discountValue").getAsBigDecimal(),
                body.has("maxDiscountAmount") && !body.get("maxDiscountAmount").isJsonNull() ? body.get("maxDiscountAmount").getAsBigDecimal() : null,
                body.get("minBookingValue").getAsBigDecimal(),
                body.has("applicableVehicleTypeId") && !body.get("applicableVehicleTypeId").isJsonNull() ? body.get("applicableVehicleTypeId").getAsInt() : null,
                body.has("maxUsagePerUser") && !body.get("maxUsagePerUser").isJsonNull() ? body.get("maxUsagePerUser").getAsInt() : null,
                Timestamp.valueOf(body.get("validFrom").getAsString().replace("T", " ")),
                Timestamp.valueOf(body.get("validTo").getAsString().replace("T", " ")),
                adminId,
                body.has("campaignId") && !body.get("campaignId").isJsonNull() ? body.get("campaignId").getAsInt() : null,
                body.has("totalQuantity") && !body.get("totalQuantity").isJsonNull() ? body.get("totalQuantity").getAsInt() : null
            );
            apiResponse.put("success", true);
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        out.print(gson.toJson(apiResponse));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        request.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        try {
            if (requireAdmin(request, response, apiResponse) == null) {
                out.print(gson.toJson(apiResponse));
                return;
            }
            int id = Integer.parseInt(request.getPathInfo().split("/")[1]);
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            Timestamp validTo = body.has("validTo") && !body.get("validTo").isJsonNull() ? Timestamp.valueOf(body.get("validTo").getAsString().replace("T", " ")) : null;
            String status = body.has("status") && !body.get("status").isJsonNull() ? body.get("status").getAsString() : null;
            Integer totalQuantity = body.has("totalQuantity") && !body.get("totalQuantity").isJsonNull() ? body.get("totalQuantity").getAsInt() : null;
            dao.updateVoucher(id, validTo, status, totalQuantity);
            apiResponse.put("success", true);
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        out.print(gson.toJson(apiResponse));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        try {
            if (requireAdmin(request, response, apiResponse) == null) {
                out.print(gson.toJson(apiResponse));
                return;
            }
            int id = Integer.parseInt(request.getPathInfo().split("/")[1]);
            dao.deleteVoucher(id);
            apiResponse.put("success", true);
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        out.print(gson.toJson(apiResponse));
    }
}
