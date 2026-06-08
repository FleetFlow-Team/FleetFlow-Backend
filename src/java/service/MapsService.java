package service;
 
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
 
public class MapsService {
 
    private static final String SERVICE_API_KEY = "16069deeb411f94746f9bd2eafb5f123aabbef05c2f21740";
    private static final double MIN_DISTANCE_KM = 20.0;
 
    /**
     * Tính khoảng cách giữa 2 điểm (km) dùng VietMap Matrix API
     * BR-01: khoảng cách tối thiểu 20km
     *
     * @param pickupLat   vĩ độ điểm đón
     * @param pickupLng   kinh độ điểm đón
     * @param dropoffLat  vĩ độ điểm trả
     * @param dropoffLng  kinh độ điểm trả
     * @return khoảng cách tính bằng km
     */
    public double calculateDistance(double pickupLat, double pickupLng,
                                    double dropoffLat, double dropoffLng) throws Exception {
        // VietMap Matrix API: point format là "lng,lat" (kinh độ trước, vĩ độ sau)
        String urlStr = "https://maps.vietmap.vn/api/matrix"
                + "?api-version=1.1"
                + "&apikey=" + SERVICE_API_KEY
                + "&point=" + pickupLng + "," + pickupLat
                + "&point=" + dropoffLng + "," + dropoffLat;
 
        String response = sendGetRequest(urlStr);
 
        // Parse JSON response
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray distances = json.getAsJsonArray("distances");
 
        // distances[0][1] = khoảng cách từ điểm 0 đến điểm 1 (tính bằng mét)
        double distanceMeters = distances.get(0).getAsJsonArray().get(1).getAsDouble();
        double distanceKm = distanceMeters / 1000.0;
 
        return Math.round(distanceKm * 10.0) / 10.0; // làm tròn 1 chữ số thập phân
    }
 
    /**
     * Validate khoảng cách theo BR-01
     * Ném exception nếu < 20km
     *
     * @return khoảng cách km nếu hợp lệ
     */
    public double validateAndGetDistance(double pickupLat, double pickupLng,
                                         double dropoffLat, double dropoffLng) throws Exception {
        double distanceKm = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
 
        if (distanceKm < MIN_DISTANCE_KM) {
            throw new IllegalArgumentException(
                "Khoảng cách quá ngắn (" + distanceKm + "km). "
                + "FleetFlow chỉ phục vụ chuyến đi từ " + MIN_DISTANCE_KM + "km trở lên."
            );
        }
 
        return distanceKm;
    }
 
    /**
     * Geocode: convert địa chỉ text → tọa độ
     * Dùng khi khách nhập địa chỉ dạng text
     *
     * @param address địa chỉ dạng text (VD: "123 Nguyễn Huệ, Quận 1, TP.HCM")
     * @return double[] { latitude, longitude }
     */
    public double[] geocode(String address) throws Exception {
        String encodedAddress = java.net.URLEncoder.encode(address, "UTF-8");
        String urlStr = "https://maps.vietmap.vn/api/geocode"
                + "?api-version=1.1"
                + "&apikey=" + SERVICE_API_KEY
                + "&address=" + encodedAddress;
 
        String response = sendGetRequest(urlStr);
 
        JsonArray results = JsonParser.parseString(response).getAsJsonArray();
 
        if (results.size() == 0) {
            throw new IllegalArgumentException("Không tìm thấy địa chỉ: " + address);
        }
 
        // Lấy kết quả đầu tiên
        JsonObject first = results.get(0).getAsJsonObject();
        double lat = first.get("lat").getAsDouble();
        double lng = first.get("lng").getAsDouble();
 
        return new double[]{lat, lng};
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
}