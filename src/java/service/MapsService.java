package service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MapsService {

    private static final String API_KEY = "16069deeb411f94746f9bd2eafb5f123aabbef05c2f21740";
    private static final double MIN_DISTANCE_KM = 20.0;

    /**
     * Tính khoảng cách giữa 2 điểm dùng VietMap Matrix API
     * BR-01: validate khoảng cách tối thiểu 20km
     */
    public double calculateDistance(double pickupLat, double pickupLng,
                                    double dropoffLat, double dropoffLng) throws Exception {
        String urlStr = "https://maps.vietmap.vn/api/matrix"
                + "?api-version=1.1"
                + "&apikey=" + API_KEY
                + "&point=" + pickupLat + "," + pickupLng
                + "&point=" + dropoffLat + "," + dropoffLng
                + "&sources=0"
                + "&destinations=1"
                + "&annotation=distance";

        String response = sendGetRequest(urlStr);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        JsonArray distances = json.getAsJsonArray("distances");
        double distanceMeters = distances.get(0).getAsJsonArray().get(0).getAsDouble();
        double distanceKm = distanceMeters / 1000.0;

        return Math.round(distanceKm * 10.0) / 10.0;
    }

    /**
     * Validate khoảng cách theo BR-01
     * Ném exception nếu < 20km
     */
    public double validateAndGetDistance(double pickupLat, double pickupLng,
                                         double dropoffLat, double dropoffLng) throws Exception {
        double distanceKm = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);

        if (distanceKm < MIN_DISTANCE_KM) {
            throw new IllegalArgumentException(
                "Khoảng cách quá ngắn (" + distanceKm + "km). "
                + "FleetFlow chỉ phục vụ chuyến đi từ " + (int) MIN_DISTANCE_KM + "km trở lên."
            );
        }

        return distanceKm;
    }

    /**
     * Geocode: convert địa chỉ text → tọa độ
     * Response format: GeoJSON FeatureCollection
     */
    public double[] geocode(String address) throws Exception {
        String encodedAddress = java.net.URLEncoder.encode(address, "UTF-8");
        String urlStr = "https://maps.vietmap.vn/api/search"
                + "?api-version=1.1"
                + "&apikey=" + API_KEY
                + "&text=" + encodedAddress;

        String response = sendGetRequest(urlStr);
        JsonObject root = JsonParser.parseString(response).getAsJsonObject();

        JsonObject data = root.getAsJsonObject("data");
        JsonArray features = data.getAsJsonArray("features");

        if (features.size() == 0) {
            throw new IllegalArgumentException("Không tìm thấy địa chỉ: " + address);
        }

        JsonObject firstFeature = features.get(0).getAsJsonObject();
        JsonObject geometry = firstFeature.getAsJsonObject("geometry");
        JsonArray coordinates = geometry.getAsJsonArray("coordinates");

        // VietMap trả về [longitude, latitude]
        double lng = coordinates.get(0).getAsDouble();
        double lat = coordinates.get(1).getAsDouble();

        return new double[]{lat, lng};
    }

    /**
     * Route: lấy đường đi giữa 2 điểm
     * Trả về encoded polyline string để frontend vẽ lên bản đồ
     * Frontend dùng VietMap SDK để decode polyline này
     *
     * @return JsonObject chứa points (encoded polyline), distance (m), time (ms), instructions
     */
    public JsonObject getRoute(double fromLat, double fromLng,
                               double toLat, double toLng) throws Exception {
        // points_encoded=true để nhận polyline nén (nhẹ hơn)
        // vehicle=car vì FleetFlow là xe ô tô
        String urlStr = "https://maps.vietmap.vn/api/route"
                + "?api-version=1.1"
                + "&apikey=" + API_KEY
                + "&point=" + fromLat + "," + fromLng
                + "&point=" + toLat + "," + toLng
                + "&points_encoded=true"
                + "&vehicle=car";

        String response = sendGetRequest(urlStr);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        // Lấy path đầu tiên (tối ưu nhất)
        JsonArray paths = json.getAsJsonArray("paths");
        if (paths == null || paths.size() == 0) {
            throw new Exception("VietMap Route API không trả về kết quả");
        }

        JsonObject bestPath = paths.get(0).getAsJsonObject();

        // Build response gọn cho frontend
        JsonObject result = new JsonObject();
        result.addProperty("points", bestPath.get("points").getAsString()); // encoded polyline
        result.addProperty("distanceMeters", bestPath.get("distance").getAsDouble());
        result.addProperty("distanceKm",
            Math.round(bestPath.get("distance").getAsDouble() / 100.0) / 10.0);
        result.addProperty("durationMs", bestPath.get("time").getAsLong());
        result.addProperty("durationMinutes",
            bestPath.get("time").getAsLong() / 60000);

        // Thêm instructions (hướng dẫn từng đoạn đường)
        if (bestPath.has("instructions")) {
            result.add("instructions", bestPath.getAsJsonArray("instructions"));
        }

        return result;
    }

    /**
     * Helper: gửi GET request và trả về response string
     */
    private String sendGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("VietMap API lỗi, response code: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), "UTF-8")
        );
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        return sb.toString();
    }

    /**
     * GET /api/v1/maps/reverse-geocode?lat=&lng=
     * Nhận tọa độ GPS từ trình duyệt/mobile của khách → gọi VietMap reverse
     * geocode để trả về tên địa chỉ thực tế (đường, phường, quận, tỉnh).
     * Dùng để tự động điền địa chỉ đón khi customer bấm "Lấy vị trí hiện tại".
     *
     * Response mẫu:
     * {
     *   "lat": 10.7769,
     *   "lng": 106.7009,
     *   "address": "194 Hoàng Diệu 2, Linh Chiểu, Thủ Đức, Hồ Chí Minh",
     *   "display": "194 Hoàng Diệu 2, Linh Chiểu, Thủ Đức, Hồ Chí Minh",
     *   "ref_id": "vm:node:123456"
     * }
     */
    public JsonObject reverseGeocode(double lat, double lng) throws Exception {
        String urlStr = "https://maps.vietmap.vn/api/reverse"
                + "?api-version=1.1"
                + "&apikey=" + API_KEY
                + "&point.lat=" + lat
                + "&point.lon=" + lng;

        String raw = sendGetRequest(urlStr);
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();

        JsonObject result = new JsonObject();
        result.addProperty("lat", lat);
        result.addProperty("lng", lng);

        JsonArray features = root.has("data")
                ? root.getAsJsonObject("data").getAsJsonArray("features")
                : root.getAsJsonArray("features");

        if (features != null && features.size() > 0) {
            JsonObject props = features.get(0).getAsJsonObject().getAsJsonObject("properties");
            // VietMap trả về label (tên địa điểm), address (địa chỉ đầy đủ), locality (phường), region (tỉnh)
            String address = getStr(props, "address");
            String label   = getStr(props, "label");
            String display = address.isEmpty() ? label : address;
            result.addProperty("address", display);
            result.addProperty("display", display);
            if (props.has("Id")) {
                result.addProperty("ref_id", props.get("Id").getAsString());
            }
        } else {
            result.addProperty("address", "");
            result.addProperty("display", "Không tìm thấy địa chỉ cho tọa độ này");
        }

        return result;
    }

    private String buildAddress(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{"name", "house_number", "street", "ward", "district", "city"}) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                String val = obj.get(key).getAsString().trim();
                if (!val.isEmpty()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(val);
                }
            }
        }
        return sb.toString();
    }

    private String getStr(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString().trim();
        }
        return "";
    }
}