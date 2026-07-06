/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.HolidayDAO;
import java.io.IOException;
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
@WebServlet("/api/v1/admin/holidays/*")
public class HolidayController extends HttpServlet {

    private final HolidayDAO dao = new HolidayDAO();
    private final Gson gson = new Gson();

    /** Chỉ Admin mới được thêm/xóa ngày lễ (BR bảo mật P2-2). GET để public. */
    private boolean requireAdmin(HttpServletRequest request, HttpServletResponse response, Map<String, Object> res) throws IOException {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !JwtUtils.validateToken(token)) {
            response.setStatus(401);
            res.put("success", false);
            res.put("message", "Unauthorized");
            response.getWriter().print(gson.toJson(res));
            return false;
        }
        if (!"Admin".equalsIgnoreCase(JwtUtils.getRoleFromToken(token))) {
            response.setStatus(403);
            res.put("success", false);
            res.put("message", "Forbidden");
            response.getWriter().print(gson.toJson(res));
            return false;
        }
        return true;
    }
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
            res.put("data", dao.getHolidays());
            response.getWriter().print(gson.toJson(res));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
            response.getWriter().print(gson.toJson(res));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> res = new HashMap<>();
        if (!requireAdmin(request, response, res)) {
            return;
        }

        try {
            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            String date = body.get("holidayDate").getAsString();
            String desc = body.has("description") ? body.get("description").getAsString() : "";

            res.put("success", dao.addHoliday(date, desc));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        String pathInfo = request.getPathInfo();
        Map<String, Object> res = new HashMap<>();
        if (!requireAdmin(request, response, res)) {
            return;
        }

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                response.setStatus(400);
                return;
            }
            res.put("success", dao.deleteHoliday(Integer.parseInt(pathInfo.substring(1))));
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }
}