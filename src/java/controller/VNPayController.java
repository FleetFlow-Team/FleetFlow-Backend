package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ExtensionDAO;
import utils.DbUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/v1/payments/vnpay/*")
public class VNPayController extends HttpServlet {

    private final Gson gson = new Gson();

    private static final String VNP_TMN_CODE = "5GH8KI9L";
    private static final String VNP_HASH_SECRET = "SZ2DWD9030XB1RB85D6J4CBTGD4SJWFZ";
    private static final String VNP_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String VNP_RETURN_URL = "http://localhost:8080/FleetFlow/api/v1/payments/vnpay/return";
    private static final String VNP_IPN_URL = "http://localhost:8080/FleetFlow/api/v1/payments/vnpay/ipn";
    private static final String FE_RETURN_URL = "http://localhost:8080/payment-result";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if ("/create".equals(path)) {
            handleCreatePayment(request, response);
        } else {
            response.setStatus(404);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if ("/return".equals(path)) {
            try {
                handleReturn(request, response);
            } catch (Exception ex) {
                Logger.getLogger(VNPayController.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if ("/ipn".equals(path)) {
            handleIpn(request, response);
        } else {
            response.setStatus(404);
        }
    }

    private void handleCreatePayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> apiResponse = new HashMap<>();

        try {

            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            int bookingId = body.get("bookingId").getAsInt();
            long amount = body.get("amount").getAsLong();
            String paymentType = body.has("paymentType") ? body.get("paymentType").getAsString() : "FINAL"; // Hoặc DEPOSIT

// Tạo giao dịch PENDING trong Database trước
            ExtensionDAO paymentDAO = new ExtensionDAO();
            boolean isCreated = paymentDAO.createPendingPayment(bookingId, paymentType, "VNPAY", amount);

            if (!isCreated) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Lỗi tạo giao dịch trong hệ thống.");
                response.getWriter().print(gson.toJson(apiResponse));
                return; // Dừng luôn, không cho gọi sang VNPay nữa
            }

            String ipAddress = request.getHeader("X-FORWARDED-FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }

            String vnp_TxnRef = bookingId + "_" + System.currentTimeMillis();

            Map<String, String> vnp_Params = new TreeMap<>();
            vnp_Params.put("vnp_Version", "2.1.0");
            vnp_Params.put("vnp_Command", "pay");
            vnp_Params.put("vnp_TmnCode", VNP_TMN_CODE);
            vnp_Params.put("vnp_Amount", String.valueOf(amount * 100));
            vnp_Params.put("vnp_CurrCode", "VND");
            vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
            vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + bookingId);
            vnp_Params.put("vnp_OrderType", "other");
            vnp_Params.put("vnp_Locale", "vn");
            vnp_Params.put("vnp_ReturnUrl", VNP_RETURN_URL);
            vnp_Params.put("vnp_IpAddr", ipAddress);

            Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));
            cld.add(Calendar.MINUTE, 15);
            vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

            StringBuilder hashData = new StringBuilder();
            StringBuilder query = new StringBuilder();
            Iterator<Map.Entry<String, String>> itr = vnp_Params.entrySet().iterator();

            while (itr.hasNext()) {
                Map.Entry<String, String> entry = itr.next();
                String fieldName = entry.getKey();
                String fieldValue = entry.getValue();
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }

            String queryUrl = query.toString();
            String vnp_SecureHash = hmacSHA512(VNP_HASH_SECRET, hashData.toString());
            queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
            String paymentUrl = VNP_URL + "?" + queryUrl;

            apiResponse.put("success", true);
            apiResponse.put("paymentUrl", paymentUrl);

        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(apiResponse));
    }

    private void handleReturn(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            Map<String, String> fields = extractVNPayParams(request);
            String vnp_SecureHash = request.getParameter("vnp_SecureHash");

            boolean isValid = verifySignature(fields, vnp_SecureHash);
            String status = "failed";

            // Lấy Booking ID
            String bookingIdStr = request.getParameter("vnp_TxnRef");
            if (bookingIdStr != null && bookingIdStr.contains("_")) {
                bookingIdStr = bookingIdStr.split("_")[0];
            }

            // Bắt các thông tin thanh toán chi tiết từ VNPay
            String bankCode = request.getParameter("vnp_BankCode"); // VD: NCB, VCB...
            String transactionNo = request.getParameter("vnp_TransactionNo"); // Mã GD trên hệ thống VNPay
            String amountStr = request.getParameter("vnp_Amount");
            String amount = "0";
            if (amountStr != null) {
                // VNPay nhân 100 số tiền, nên phải chia 100 để trả về VNĐ thực tế
                amount = String.valueOf(Long.parseLong(amountStr) / 100);
            }

            // Kiểm tra chữ ký và mã phản hồi 00 (Thành công)
            if (isValid && "00".equals(request.getParameter("vnp_ResponseCode"))) {
                status = "success";
            }

            // Đóng gói toàn bộ data gửi sang trang JSP
            request.setAttribute("status", status);
            request.setAttribute("bookingId", bookingIdStr);
            request.setAttribute("amount", amount);
            request.setAttribute("bankCode", bankCode);
            request.setAttribute("transactionNo", transactionNo);

            // Forward thẳng sang trang JSP (Cách này giấu được URL tham số dài ngoằng của VNPay)
            request.getRequestDispatcher("/payment-success.jsp").forward(request, response);

        } catch (Exception e) {
            response.getWriter().print("Lỗi xử lý Return: " + e.getMessage());
        }
    }

    private void handleIpn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        JsonObject result = new JsonObject();

        try {
            Map<String, String> fields = extractVNPayParams(request);
            String vnp_SecureHash = request.getParameter("vnp_SecureHash");
            String vnp_TxnRef = request.getParameter("vnp_TxnRef");
            String vnp_ResponseCode = request.getParameter("vnp_ResponseCode");

            boolean isValid = verifySignature(fields, vnp_SecureHash);

            if (isValid) {
                if ("00".equals(vnp_ResponseCode)) {
                    int bookingId = Integer.parseInt(vnp_TxnRef.split("_")[0]);
                    savePaymentToDatabase(bookingId, "COMPLETED");

                    result.addProperty("RspCode", "00");
                    result.addProperty("Message", "Confirm Success");
                } else {
                    result.addProperty("RspCode", "24");
                    result.addProperty("Message", "Payment Failed");
                }
            } else {
                result.addProperty("RspCode", "97");
                result.addProperty("Message", "Invalid Signature");
            }
        } catch (Exception e) {
            result.addProperty("RspCode", "99");
            result.addProperty("Message", "Unknown Error");
        }
        response.getWriter().print(result.toString());
    }

    private void savePaymentToDatabase(int bookingId, String status) {
        String sql = "UPDATE Payment SET Status = ?, PaidAt = GETDATE() WHERE BookingID = ? AND Status = 'PENDING' AND Method = 'VNPAY'";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, bookingId);
            ps.executeUpdate();
            System.out.println(">> VNPay IPN: Đã cập nhật thành công Booking " + bookingId);
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật DB: " + e.getMessage());
        }
    }

    private Map<String, String> extractVNPayParams(HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0) && fieldName.startsWith("vnp_")) {
                fields.put(fieldName, fieldValue);
            }
        }
        return fields;
    }

    private boolean verifySignature(Map<String, String> fields, String secureHash) throws Exception {
        if (fields.containsKey("vnp_SecureHashType")) {
            fields.remove("vnp_SecureHashType");
        }
        if (fields.containsKey("vnp_SecureHash")) {
            fields.remove("vnp_SecureHash");
        }

        StringBuilder hashData = new StringBuilder();
        Iterator<Map.Entry<String, String>> itr = fields.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            hashData.append(entry.getKey());
            hashData.append('=');
            hashData.append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString()));
            if (itr.hasNext()) {
                hashData.append('&');
            }
        }
        String calculatedHash = hmacSHA512(VNP_HASH_SECRET, hashData.toString());
        return calculatedHash.equalsIgnoreCase(secureHash);
    }

    private String hmacSHA512(String key, String data) throws Exception {
        Mac hmac512 = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        hmac512.init(secretKey);
        byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(2 * result.length);
        for (byte b : result) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
