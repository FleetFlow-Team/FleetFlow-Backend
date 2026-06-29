package service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.VehicleAIData;

/**
 * BE-67: AI Chat & Tags — nhận câu hỏi của customer, build prompt kèm danh sách
 * xe + tags từ DB, gọi Gemini để lấy gợi ý xe đã ranking, fallback sang filter
 * thủ công (theo từ khóa) nếu gọi Gemini lỗi/timeout.
 */
public class GeminiService {

    // TODO: chuyển sang config/biến môi trường trước khi submit (đừng commit key thật lên Git)
    private static final String API_KEY = "";

    private static final String MODEL = "gemini-2.5-flash";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 30000;

    /**
     * Gọi Gemini với 1 prompt thuần text — giữ lại cho mục đích test nhanh
     * (/api/v1/ai/test).
     */
    public String askGemini(String prompt) throws Exception {
        String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\""
                + escapeJson(prompt) + "\"}]}]}";
        return callGemini(jsonBody);
    }

    /**
     * BE-67 — Endpoint chính: nhận câu hỏi customer, build prompt kèm context
     * xe trong DB, gọi Gemini, parse JSON trả về thành list gợi ý xe đã
     * ranking. Nếu Gemini lỗi (timeout, hết quota free tier, parse JSON thất
     * bại...) → fallback sang filter thủ công theo từ khóa trên
     * tags/description/brand/model.
     */
    public List<Map<String, Object>> recommendVehicles(String customerMessage, List<VehicleAIData> vehicles) {
        try {
            String prompt = buildPrompt(customerMessage, vehicles);
            String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\""
                    + escapeJson(prompt) + "\"}]}]}";

            String rawResponse = callGemini(jsonBody);
            String geminiText = extractTextFromGeminiResponse(rawResponse);
            List<Map<String, Object>> parsed = parseRecommendationJson(geminiText, vehicles);

            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }
            // Gemini trả về rỗng/không parse được → fallback
            return manualFilter(customerMessage, vehicles);

        } catch (Exception e) {
            // THÊM DÒNG NÀY để xem lỗi thật trong Tomcat log
            System.err.println("[GeminiService] Lỗi gọi Gemini: " + e.getMessage());
            e.printStackTrace();
            return manualFilter(customerMessage, vehicles);
        }
    }

    // ---------------------------------------------------------------------
    // Build prompt
    // ---------------------------------------------------------------------
    private String buildPrompt(String customerMessage, List<VehicleAIData> vehicles) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý gợi ý xe cho nền tảng thuê xe có lái FleetFlow. ");
        sb.append("Dưới đây là danh sách xe hiện có (định dạng JSON), mỗi xe có vehicleId, brand, model, ");
        sb.append("vehicleType, seatCount, description, tags (mô tả đặc điểm xe, có thể trống). ");
        sb.append("Dựa vào câu hỏi/khẩu cầu của khách hàng, hãy chọn và XẾP HẠNG (ranking) các xe phù hợp nhất lên đầu.\n\n");

        sb.append("Danh sách xe:\n");
        sb.append(vehiclesToJson(vehicles));
        sb.append("\n\n");

        sb.append("Câu hỏi của khách hàng: \"").append(customerMessage).append("\"\n\n");

        sb.append("YÊU CẦU BẮT BUỘC: Chỉ trả lời DUY NHẤT một JSON array hợp lệ, KHÔNG kèm markdown, ");
        sb.append("KHÔNG kèm dấu ```, KHÔNG giải thích gì thêm ngoài JSON. ");
        sb.append("Định dạng từng phần tử: {\"vehicleId\": <int>, \"reason\": \"<lý do ngắn gọn bằng tiếng Việt>\"}. ");
        sb.append("Chỉ trả về tối đa 5 xe phù hợp nhất, xe phù hợp nhất đứng đầu danh sách. ");
        sb.append("Nếu không có xe nào phù hợp, trả về mảng rỗng [].");

        return sb.toString();
    }

    private String vehiclesToJson(List<VehicleAIData> vehicles) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vehicles.size(); i++) {
            VehicleAIData v = vehicles.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"vehicleId\":").append(v.getVehicleId()).append(",");
            sb.append("\"brand\":\"").append(escapeJson(v.getBrand())).append("\",");
            sb.append("\"model\":\"").append(escapeJson(v.getModel())).append("\",");
            sb.append("\"vehicleType\":\"").append(escapeJson(v.getVehicleType())).append("\",");
            sb.append("\"seatCount\":").append(v.getSeatCount() == null ? 0 : v.getSeatCount()).append(",");
            sb.append("\"description\":\"").append(escapeJson(v.getDescription())).append("\",");
            sb.append("\"tags\":\"").append(escapeJson(v.getTags())).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Call Gemini API
    // ---------------------------------------------------------------------
    private String callGemini(String jsonBody) throws Exception {
        System.setProperty("https.protocols", "TLSv1.2");
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent?key=" + API_KEY;

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        try ( OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder response = new StringBuilder();
        try ( BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        if (status < 200 || status >= 300) {
            throw new Exception("Gemini API trả lỗi HTTP " + status + ": " + response);
        }

        return response.toString();
    }

    /**
     * Trích text trong candidates[0].content.parts[0].text của response Gemini.
     */
    private String extractTextFromGeminiResponse(String rawJson) throws Exception {
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0) {
            throw new Exception("Gemini không trả về candidates");
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.size() == 0) {
            throw new Exception("Gemini không trả về parts");
        }
        return parts.get(0).getAsJsonObject().get("text").getAsString();
    }

    /**
     * Parse JSON array gợi ý xe từ text Gemini trả về (có thể kèm code fence
     * ```json ... ```). Trả về list map {vehicleId, reason, brand, model,
     * vehicleType, seatCount, tags}.
     */
    private List<Map<String, Object>> parseRecommendationJson(String geminiText, List<VehicleAIData> vehicles) {
        try {
            String cleaned = geminiText.trim();
            // Gemini hay kèm ```json ... ``` dù đã yêu cầu không kèm — strip code fence nếu có
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 3);
                }
                cleaned = cleaned.trim();
            }

            JsonElement el = JsonParser.parseString(cleaned);
            if (!el.isJsonArray()) {
                return null;
            }
            JsonArray arr = el.getAsJsonArray();

            Map<Integer, VehicleAIData> byId = new LinkedHashMap<>();
            for (VehicleAIData v : vehicles) {
                byId.put(v.getVehicleId(), v);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject item = arr.get(i).getAsJsonObject();
                if (!item.has("vehicleId")) {
                    continue;
                }
                int vehicleId = item.get("vehicleId").getAsInt();
                VehicleAIData v = byId.get(vehicleId);
                if (v == null) {
                    continue; // Gemini bịa vehicleId không tồn tại trong DB → bỏ qua
                }

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("vehicleId", v.getVehicleId());
                m.put("brand", v.getBrand());
                m.put("model", v.getModel());
                m.put("vehicleType", v.getVehicleType());
                m.put("seatCount", v.getSeatCount());
                m.put("tags", v.getTags());
                m.put("reason", item.has("reason") ? item.get("reason").getAsString() : null);
                m.put("source", "AI");
                result.add(m);
            }
            return result;

        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Fallback: filter thủ công theo từ khóa khi Gemini lỗi/hết quota free tier
    // ---------------------------------------------------------------------
    private List<Map<String, Object>> manualFilter(String customerMessage, List<VehicleAIData> vehicles) {
        String keyword = customerMessage == null ? "" : customerMessage.toLowerCase().trim();
        List<Map<String, Object>> result = new ArrayList<>();

        for (VehicleAIData v : vehicles) {
            String haystack = (safe(v.getBrand()) + " " + safe(v.getModel()) + " "
                    + safe(v.getVehicleType()) + " " + safe(v.getDescription()) + " " + safe(v.getTags()))
                    .toLowerCase();

            boolean matched = keyword.isEmpty() || containsAnyWord(haystack, keyword);
            if (matched) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("vehicleId", v.getVehicleId());
                m.put("brand", v.getBrand());
                m.put("model", v.getModel());
                m.put("vehicleType", v.getVehicleType());
                m.put("seatCount", v.getSeatCount());
                m.put("tags", v.getTags());
                m.put("reason", "Gợi ý theo từ khóa (AI tạm thời không khả dụng)");
                m.put("source", "FALLBACK");
                result.add(m);
            }
            if (result.size() >= 5) {
                break;
            }
        }

        // Không có xe khớp từ khóa nào → trả về tối đa 5 xe Available đầu tiên để không bỏ trống kết quả
        if (result.isEmpty()) {
            for (int i = 0; i < Math.min(5, vehicles.size()); i++) {
                VehicleAIData v = vehicles.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("vehicleId", v.getVehicleId());
                m.put("brand", v.getBrand());
                m.put("model", v.getModel());
                m.put("vehicleType", v.getVehicleType());
                m.put("seatCount", v.getSeatCount());
                m.put("tags", v.getTags());
                m.put("reason", "Gợi ý mặc định (AI tạm thời không khả dụng)");
                m.put("source", "FALLBACK");
                result.add(m);
            }
        }

        return result;
    }

    private boolean containsAnyWord(String haystack, String keyword) {
        for (String w : keyword.split("\\s+")) {
            if (w.length() >= 2 && haystack.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
