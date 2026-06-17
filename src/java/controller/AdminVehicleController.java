/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.VehicleDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Account;

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/admin/vehicles/*")
public class AdminVehicleController extends HttpServlet {
 
    private final VehicleDAO vehicleDAO = new VehicleDAO();
 
    private void setAccessControlHeaders(HttpServletRequest request, HttpServletResponse response) {
        String clientOrigin = request.getHeader("Origin");
 
        if (clientOrigin != null) {
            response.setHeader("Access-Control-Allow-Origin", clientOrigin);
        } else {
            response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5500");
        }
 
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
    }
 
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        setAccessControlHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
 
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        setAccessControlHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
 
        Account admin = getAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }
 
        String pathInfo = request.getPathInfo();
 
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                List<Map<String, Object>> vehicles = vehicleDAO.findAll();
 
                StringBuilder json = new StringBuilder();
                json.append("{\"success\": true, \"count\": ").append(vehicles.size()).append(", \"data\": [");
                for (int i = 0; i < vehicles.size(); i++) {
                    if (i > 0) {
                        json.append(",");
                    }
                    json.append(mapToJson(vehicles.get(i)));
                }
                json.append("]}");
 
                response.setStatus(200);
                out.print(json.toString());
 
            } else {
                Integer id = parseId(pathInfo);
                if (id == null) {
                    response.setStatus(400);
                    out.print("{\"error\": \"VehicleID không hợp lệ\"}");
                    return;
                }
 
                Map<String, Object> vehicle = vehicleDAO.findById(id);
                if (vehicle == null) {
                    response.setStatus(404);
                    out.print("{\"error\": \"Không tìm thấy xe #" + id + "\"}");
                } else {
                    response.setStatus(200);
                    out.print("{\"success\": true, \"data\": " + mapToJson(vehicle) + "}");
                }
            }
 
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }
 
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        setAccessControlHeaders(request, response);
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
 
        Account admin = getAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }
 
        try {
            JsonObject body = readJsonBody(request);
 
            Integer vehicleTypeId = getInt(body, "vehicleTypeId");
            String licensePlate = getStr(body, "licensePlate");
            String brand = getStr(body, "brand");
            String model = getStr(body, "model");
            Integer seatCount = getInt(body, "seatCount");
 
            if (vehicleTypeId == null || licensePlate == null || brand == null
                    || model == null || seatCount == null) {
                response.setStatus(400);
                out.print("{\"error\": \"Thiếu trường bắt buộc: vehicleTypeId, licensePlate, brand, model, seatCount\"}");
                return;
            }
 
            String chassisNumber = getStr(body, "chassisNumber");
            String engineNumber = getStr(body, "engineNumber");
            String status = getStr(body, "status");
            if (status == null) {
                status = "Available";
            }
            Integer accumulatedKm = getInt(body, "accumulatedKm");
            String description = getStr(body, "description");
            int createdBy = (int) admin.getId();
 
            int newId = vehicleDAO.create(vehicleTypeId, licensePlate, chassisNumber, engineNumber,
                    brand, model, seatCount, status, accumulatedKm, createdBy, description);
 
            response.setStatus(201);
            out.print("{\"success\": true, \"message\": \"Tạo xe thành công\", \"vehicleId\": " + newId
                    + ", \"data\": " + mapToJson(vehicleDAO.findById(newId)) + "}");
 
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }
 
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        setAccessControlHeaders(request, response);
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
 
        Account admin = getAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }
 
        Integer id = parseId(request.getPathInfo());
        if (id == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu hoặc sai VehicleID\"}");
            return;
        }
 
        try {
            if (vehicleDAO.findById(id) == null) {
                response.setStatus(404);
                out.print("{\"error\": \"Không tìm thấy xe #" + id + "\"}");
                return;
            }
 
            JsonObject body = readJsonBody(request);
            Integer vehicleTypeId = getInt(body, "vehicleTypeId");
            String licensePlate = getStr(body, "licensePlate");
            String chassisNumber = getStr(body, "chassisNumber");
            String engineNumber = getStr(body, "engineNumber");
            String brand = getStr(body, "brand");
            String model = getStr(body, "model");
            Integer seatCount = getInt(body, "seatCount");
            String status = getStr(body, "status");
            Integer accumulatedKm = getInt(body, "accumulatedKm");
            String description = getStr(body, "description");
 
            vehicleDAO.update(id, vehicleTypeId, licensePlate, chassisNumber, engineNumber,
                    brand, model, seatCount, status, accumulatedKm, description);
 
            response.setStatus(200);
            out.print("{\"success\": true, \"message\": \"Cập nhật xe thành công\", \"data\": "
                    + mapToJson(vehicleDAO.findById(id)) + "}");
 
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }
 
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        setAccessControlHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
 
        Account admin = getAdminAccount(request, response, out);
        if (admin == null) {
            return;
        }
 
        Integer id = parseId(request.getPathInfo());
        if (id == null) {
            response.setStatus(400);
            out.print("{\"error\": \"Thiếu hoặc sai VehicleID\"}");
            return;
        }
 
        try {
            if (vehicleDAO.findById(id) == null) {
                response.setStatus(404);
                out.print("{\"error\": \"Không tìm thấy xe #" + id + "\"}");
                return;
            }
 
            int bookings = vehicleDAO.countBookingsByVehicle(id);
            if (bookings > 0) {
                response.setStatus(409);
                out.print("{\"error\": \"Không thể xóa: xe đang có " + bookings
                        + " lịch sử đặt. Hãy đổi Status sang Unavailable thay vì xóa\"}");
                return;
            }
 
            vehicleDAO.deleteWithChildren(id);
            response.setStatus(200);
            out.print("{\"success\": true, \"message\": \"Xóa xe thành công\"}");
 
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\": \"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
    }
 
    private Account getAdminAccount(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("account") == null) {
            response.setStatus(401);
            out.print("{\"error\": \"Chưa đăng nhập\"}");
            return null;
        }
        Account acc = (Account) session.getAttribute("account");
        if (acc.getRoleName() == null || !acc.getRoleName().equalsIgnoreCase("Admin")) {
            response.setStatus(403);
            out.print("{\"error\": \"Chỉ tài khoản Admin được truy cập chức năng này\"}");
            return null;
        }
        return acc;
    }
 
    private JsonObject readJsonBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }
 
    private String getStr(JsonObject body, String key) {
        if (body.has(key) && !body.get(key).isJsonNull()) {
            String v = body.get(key).getAsString().trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }
 
    private Integer getInt(JsonObject body, String key) {
        if (body.has(key) && !body.get(key).isJsonNull()) {
            return body.get(key).getAsInt();
        }
        return null;
    }
 
    private Integer parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.equals("/")) {
            return null;
        }
        String idStr = pathInfo.substring(1).split("/")[0];
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
 
    private String mapToJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(esc(entry.getKey())).append("\":");
            appendValue(sb, entry.getValue());
            i++;
        }
        sb.append("}");
        return sb.toString();
    }
 
    private void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else {
            sb.append("\"").append(esc(value.toString())).append("\"");
        }
    }
 
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

 