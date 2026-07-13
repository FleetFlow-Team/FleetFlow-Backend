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
import model.VehicleAIData;
import service.GeminiService;

/**
 * BE-67: Module AI - Chat & Tags
 * POST /api/v1/ai/chat — Nhận câu hỏi từ customer → query xe + tags từ DB
 *                          → build prompt Gemini → trả gợi ý xe.
 * GET  /api/v1/ai/test  — endpoint test nhanh kết nối Gemini (giữ để debug).
 */
@WebServlet("/api/v1/ai/*")
public class AIController extends HttpServlet {

    private final GeminiService geminiService = new GeminiService();
    private final VehicleDAO vehicleDAO = new VehicleDAO();

    private void setAccessControlHeaders(HttpServletRequest request, HttpServletResponse response) {
        String clientOrigin = request.getHeader("Origin");
        if (clientOrigin != null) {
            response.setHeader("Access-Control-Allow-Origin", clientOrigin);
        } else {
            response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:5500");
        }
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
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

        String pathInfo = request.getPathInfo();

        try {
            if ("/test".equals(pathInfo)) {
                String result = geminiService.askGemini("Xin chào Gemini");
                out.print(result);
            } else {
                response.setStatus(404);
                out.print("{\"error\":\"Endpoint không tồn tại\"}");
            }
        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    /**
     * BE-67: POST /api/v1/ai/chat
     * Body: {"message": "tôi cần xe 7 chỗ đi Đà Lạt, cốp rộng"}
     * Response: {"success": true, "source": "AI" | "FALLBACK", "data": [...]}
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(request, response);
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String pathInfo = request.getPathInfo();
        if (!"/chat".equals(pathInfo)) {
            response.setStatus(404);
            out.print("{\"error\":\"Endpoint không tồn tại\"}");
            return;
        }

        try {
            JsonObject body = readJsonBody(request);
            String message = getStr(body, "message");
            if (message == null) {
                response.setStatus(400);
                out.print("{\"error\":\"Thiếu trường 'message'\"}");
                return;
            }

            List<VehicleAIData> vehicles = vehicleDAO.getVehiclesForAI();
            if (vehicles == null || vehicles.isEmpty()) {
                response.setStatus(200);
                out.print("{\"success\": true, \"source\": \"NONE\", \"data\": [], "
                        + "\"message\": \"Hiện chưa có xe nào trong hệ thống\"}");
                return;
            }

            List<Map<String, Object>> suggestions = geminiService.recommendVehicles(message, vehicles);

            String source = suggestions.isEmpty() ? "NONE"
                    : String.valueOf(suggestions.get(0).get("source"));

            if ("OFF_TOPIC".equals(source) || "UNREALISTIC".equals(source)) {
                String botMessage = String.valueOf(suggestions.get(0).get("message"));
                response.setStatus(200);
                out.print("{\"success\": true, \"source\": \"" + source + "\", \"data\": [], "
                        + "\"message\": \"" + esc(botMessage) + "\"}");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"success\": true, \"source\": \"").append(source).append("\", \"data\": [");
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append(mapToJson(suggestions.get(i)));
            }
            json.append("]}");

            response.setStatus(200);
            out.print(json.toString());

        } catch (Exception e) {
            response.setStatus(500);
            out.print("{\"error\":\"Lỗi server: " + esc(e.getMessage()) + "\"}");
        }
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