/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.PricingRuleDAO;
import java.io.IOException;
import java.math.BigDecimal;
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
@WebServlet("/api/v1/admin/pricing-rules/*")
public class PricingRuleController extends HttpServlet {

    private final PricingRuleDAO dao = new PricingRuleDAO();
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();
        try {
            res.put("success", true);
            res.put("data", dao.getPricingRules());
            response.getWriter().print(gson.toJson(res));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
            response.getWriter().print(gson.toJson(res));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        String pathInfo = request.getPathInfo();
        Map<String, Object> res = new HashMap<>();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(400);
                return;
            }
            int ruleId = Integer.parseInt(pathInfo.substring(1));
            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();

            BigDecimal basePrice = body.has("basePrice") ? body.get("basePrice").getAsBigDecimal() : BigDecimal.ZERO;
            BigDecimal pricePerKm = body.has("pricePerKm") ? body.get("pricePerKm").getAsBigDecimal() : BigDecimal.ZERO;
            BigDecimal pricePerHour = body.has("pricePerHour") ? body.get("pricePerHour").getAsBigDecimal() : BigDecimal.ZERO;
            BigDecimal pricePerDay = body.has("pricePerDay") ? body.get("pricePerDay").getAsBigDecimal() : BigDecimal.ZERO;
            BigDecimal weekendMultiplier = body.has("weekendMultiplier") ? body.get("weekendMultiplier").getAsBigDecimal() : BigDecimal.ONE;

            res.put("success", dao.updatePricingRule(ruleId, basePrice, pricePerKm, pricePerHour, pricePerDay, weekendMultiplier));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }
}