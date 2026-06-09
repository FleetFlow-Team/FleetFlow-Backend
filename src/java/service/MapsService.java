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

        // distances[0][0] = khoảng cách từ source 0 đến destination 0 (mét)
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
     * coordinates = [longitude, latitude] (thứ tự lng trước, lat sau)
     */
    public double[] geocode(String address) throws Exception {
        String encodedAddress = java.net.URLEncoder.encode(address, "UTF-8");
        String urlStr = "https://maps.vietmap.vn/api/search"
                + "?api-version=1.1"
                + "&apikey=" + API_KEY
                + "&text=" + encodedAddress;

        String response = sendGetRequest(urlStr);
        JsonObject root = JsonParser.parseString(response).getAsJsonObject();

        // Lấy data.features[0].geometry.coordinates
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