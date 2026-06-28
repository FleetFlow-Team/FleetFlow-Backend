package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ExtensionDAO;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/payments/momo/create")
public class PaymentController extends HttpServlet {

    private final ExtensionDAO dao = new ExtensionDAO();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private static final String MOMO_API_URL = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final String PARTNER_CODE = "MOMOBKUN20180529"; 
    private static final String ACCESS_KEY = "klm05TvNBzhg7h7j"; 
    private static final String SECRET_KEY = "at67qH6mk8w5Y1nAyMoYKMWACiEi2bsa";
    
    // Đã đưa về chuẩn localhost hoàn toàn để phục vụ demo nội bộ
    private static final String IPN_URL = "http://localhost:8080/FleetFlow/api/v1/payments/momo/callback"; 
    private static final String REDIRECT_URL = "http://localhost:8080/FleetFlow/customer/payment-success.html"; 

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        prepare(response);
        request.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();
        
        try {
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            
            int bookingId = body.get("bookingId").getAsInt(); 
            String paymentType = body.has("paymentType") ? body.get("paymentType").getAsString() : "FINAL";
            String amountStr = body.get("amount").getAsString();
            
            int paymentId = dao.createPayment(bookingId, paymentType, body.get("amount").getAsBigDecimal());
            
            String orderId = String.valueOf(paymentId);
            String requestId = orderId + "_" + System.currentTimeMillis();
            String orderInfo = "Thanh toan FleetFlow don hang " + orderId;
            String requestType = "captureWallet";

            String rawSignature = "accessKey=" + ACCESS_KEY + "&amount=" + amountStr + "&extraData=&ipnUrl=" + IPN_URL +
                    "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&partnerCode=" + PARTNER_CODE +
                    "&redirectUrl=" + REDIRECT_URL + "&requestId=" + requestId + "&requestType=" + requestType;

            String signature = signHmacSHA256(rawSignature, SECRET_KEY);

            JsonObject momoRequest = new JsonObject();
            momoRequest.addProperty("partnerCode", PARTNER_CODE);
            momoRequest.addProperty("partnerName", "FleetFlow");
            momoRequest.addProperty("storeId", "FleetFlowStore");
            momoRequest.addProperty("requestId", requestId);
            momoRequest.addProperty("amount", amountStr);
            momoRequest.addProperty("orderId", orderId);
            momoRequest.addProperty("orderInfo", orderInfo);
            momoRequest.addProperty("redirectUrl", REDIRECT_URL);
            momoRequest.addProperty("ipnUrl", IPN_URL);
            momoRequest.addProperty("lang", "vi");
            momoRequest.addProperty("extraData", "");
            momoRequest.addProperty("requestType", requestType);
            momoRequest.addProperty("signature", signature);

            String momoResponseStr = sendPostRequest(MOMO_API_URL, momoRequest.toString());
            JsonObject momoResponse = JsonParser.parseString(momoResponseStr).getAsJsonObject();

            if (momoResponse.get("resultCode").getAsInt() == 0) {
                apiResponse.put("success", true);
                apiResponse.put("paymentUrl", momoResponse.get("payUrl").getAsString());
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", momoResponse.get("message").getAsString());
            }
        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", "Lỗi tạo link MoMo: " + e.getMessage()); 
        }
        out.print(gson.toJson(apiResponse));
    }

    private String signHmacSHA256(String data, String secretKey) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String sendPostRequest(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            wr.flush();
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) response.append(inputLine);
        }
        return response.toString();
    }
}