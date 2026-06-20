/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.VehicleDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
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
 * @author User
 */
@WebServlet("/api/v1/vehicles/*")
public class VehicleController extends HttpServlet {
private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();
 
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
 
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
 
        try {
            String pathInfo = request.getPathInfo();
 
            if (pathInfo == null || pathInfo.equals("/")) {
                String seatCountStr = request.getParameter("seatCount");
                String brand = request.getParameter("brand");
                String fuelType = request.getParameter("fuelType");
                String bookingType = request.getParameter("bookingType");
                String priceMinStr = request.getParameter("priceMin");
                String priceMaxStr = request.getParameter("priceMax");
                
                Integer seatCount = (seatCountStr != null && !seatCountStr.trim().isEmpty()) ? Integer.valueOf(seatCountStr.trim()) : null;
                BigDecimal priceMin = (priceMinStr != null && !priceMinStr.trim().isEmpty()) ? new BigDecimal(priceMinStr.trim()) : null;
                BigDecimal priceMax = (priceMaxStr != null && !priceMaxStr.trim().isEmpty()) ? new BigDecimal(priceMaxStr.trim()) : null;

                if (bookingType != null) {
                    bookingType = bookingType.trim().toUpperCase();
                    if (bookingType.equals("CHUYẾN")) bookingType = "DISTANCE";
                    else if (bookingType.equals("GIỜ")) bookingType = "HOURLY";
                    else if (bookingType.equals("NGÀY")) bookingType = "DAILY";
                }

                List<Map<String, Object>> vehicles = vehicleDAO.findVehiclesWithFilters(
                        seatCount, brand, fuelType, bookingType, priceMin, priceMax
                );
                
                apiResponse.put("success", true);
                apiResponse.put("count", vehicles.size());
                apiResponse.put("data", vehicles);
 
            } else {
                String idStr = pathInfo.substring(1).split("/")[0];
                int vehicleId;
                try {
                    vehicleId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    apiResponse.put("success", false);
                    apiResponse.put("message", "VehicleID không hợp lệ.");
                    out.print(gson.toJson(apiResponse));
                    out.flush();
                    return;
                }
 
                Map<String, Object> vehicle = vehicleDAO.findById(vehicleId);
                if (vehicle == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy xe #" + vehicleId);
                } else {
                    apiResponse.put("success", true);
                    apiResponse.put("data", vehicle);
                }
            }
 
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            apiResponse.put("success", false);
            apiResponse.put("message", "Lỗi hệ thống: " + e.getMessage());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }
 
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
 
    private Integer parseIntParam(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}