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
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static final String VNP_QUERY_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final String VNP_RETURN_URL = "http://localhost:8080/FleetFlow/api/v1/payments/vnpay/return";
    private static final String VNP_IPN_URL = "http://localhost:8080/FleetFlow/api/v1/payments/vnpay/ipn";
    private static final String FE_RETURN_URL = "http://localhost:3000/payment-result";

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
        } else if ("/query".equals(path)) {
            handleQueryDR(request, response);
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

            // vnp_TxnRef được sinh trước, rồi lưu thẳng làm TransactionRef trong DB —
            // để sau này IPN/QueryDR match update đúng chính xác giao dịch này,
            // không đoán mò theo BookingID (dễ đụng nhầm các payment PENDING khác của cùng booking).
            String vnp_TxnRef = bookingId + "_" + System.currentTimeMillis();

            // Tạo giao dịch PENDING trong Database trước
            ExtensionDAO paymentDAO = new ExtensionDAO();
            boolean isCreated = paymentDAO.createPendingPayment(bookingId, paymentType, "VNPAY", amount, vnp_TxnRef);

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
            String vnp_TxnRef = request.getParameter("vnp_TxnRef");
            String bookingIdStr = vnp_TxnRef;
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
            } else if (isValid) {
                // Chữ ký hợp lệ (dữ liệu thật từ VNPay) nhưng response code khác 00 —
                // giao dịch thất bại/khách hủy. Đánh dấu FAILED ngay ở đây thay vì để
                // Payment kẹt ở PENDING mãi, khiến khách không thanh toán lại được
                // (không chờ IPN vì IPN không gọi được vào localhost khi dev).
                savePaymentToDatabase(vnp_TxnRef, "FAILED");
            }

            // Redirect sang FE kèm kết quả thanh toán qua query params (backend không render HTML)
            // txnRef được gửi kèm để FE có thể gọi /query (QueryDR) xác nhận lại trạng thái giao dịch với VNPay
            String redirectUrl = FE_RETURN_URL
                    + "?status=" + URLEncoder.encode(status, StandardCharsets.UTF_8.toString())
                    + "&bookingId=" + URLEncoder.encode(bookingIdStr == null ? "" : bookingIdStr, StandardCharsets.UTF_8.toString())
                    + "&amount=" + URLEncoder.encode(amount, StandardCharsets.UTF_8.toString())
                    + "&bankCode=" + URLEncoder.encode(bankCode == null ? "" : bankCode, StandardCharsets.UTF_8.toString())
                    + "&transactionNo=" + URLEncoder.encode(transactionNo == null ? "" : transactionNo, StandardCharsets.UTF_8.toString())
                    + "&txnRef=" + URLEncoder.encode(vnp_TxnRef == null ? "" : vnp_TxnRef, StandardCharsets.UTF_8.toString());

            response.sendRedirect(redirectUrl);

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
                    savePaymentToDatabase(vnp_TxnRef, "COMPLETED");

                    result.addProperty("RspCode", "00");
                    result.addProperty("Message", "Confirm Success");
                } else {
                    savePaymentToDatabase(vnp_TxnRef, "FAILED");
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

    /**
     * Chủ động hỏi lại VNPay trạng thái thật của giao dịch (QueryDR).
     * Đây là lệnh gọi outbound (backend -> VNPay) nên chạy được cả khi backend
     * đang chạy localhost, không cần VNPay gọi ngược vào /ipn (server-to-server,
     * cần backend có địa chỉ public mới nhận được).
     */
    private void handleQueryDR(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String txnRef = request.getParameter("txnRef");
            if (txnRef == null || !txnRef.contains("_")) {
                response.setStatus(400);
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu hoặc sai định dạng txnRef (kỳ vọng dạng bookingId_epochMillis)");
                response.getWriter().print(gson.toJson(apiResponse));
                return;
            }

            int bookingId = Integer.parseInt(txnRef.split("_")[0]);
            long createdMillis = Long.parseLong(txnRef.split("_")[1]);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("Etc/GMT+7"));
            // vnp_TransactionDate phải khớp với vnp_CreateDate đã gửi lúc tạo giao dịch (/create),
            // suy ra lại từ epochMillis nhúng sẵn trong vnp_TxnRef nên không cần lưu thêm cột DB nào.
            String vnp_TransactionDate = formatter.format(new Date(createdMillis));
            String vnp_CreateDate = formatter.format(new Date());

            String ipAddress = request.getHeader("X-FORWARDED-FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }

            String vnp_RequestId = String.valueOf(System.currentTimeMillis());
            String vnp_Version = "2.1.0";
            String vnp_Command = "querydr";
            String vnp_OrderInfo = "Kiem tra ket qua giao dich " + txnRef;

            String hashData = String.join("|",
                    vnp_RequestId, vnp_Version, vnp_Command, VNP_TMN_CODE,
                    txnRef, vnp_TransactionDate, vnp_CreateDate, ipAddress, vnp_OrderInfo);
            String vnp_SecureHash = hmacSHA512(VNP_HASH_SECRET, hashData);

            JsonObject queryRequest = new JsonObject();
            queryRequest.addProperty("vnp_RequestId", vnp_RequestId);
            queryRequest.addProperty("vnp_Version", vnp_Version);
            queryRequest.addProperty("vnp_Command", vnp_Command);
            queryRequest.addProperty("vnp_TmnCode", VNP_TMN_CODE);
            queryRequest.addProperty("vnp_TxnRef", txnRef);
            queryRequest.addProperty("vnp_OrderInfo", vnp_OrderInfo);
            queryRequest.addProperty("vnp_TransactionDate", vnp_TransactionDate);
            queryRequest.addProperty("vnp_CreateDate", vnp_CreateDate);
            queryRequest.addProperty("vnp_IpAddr", ipAddress);
            queryRequest.addProperty("vnp_SecureHash", vnp_SecureHash);

            String vnpResponseStr = sendPostRequest(VNP_QUERY_URL, queryRequest.toString());
            JsonObject vnpResponse = JsonParser.parseString(vnpResponseStr).getAsJsonObject();

            String responseCode = vnpResponse.has("vnp_ResponseCode") ? vnpResponse.get("vnp_ResponseCode").getAsString() : null;
            String transactionStatus = vnpResponse.has("vnp_TransactionStatus") ? vnpResponse.get("vnp_TransactionStatus").getAsString() : null;
            boolean paid = "00".equals(responseCode) && "00".equals(transactionStatus);

            if (paid) {
                savePaymentToDatabase(txnRef, "COMPLETED");
            } else if ("00".equals(responseCode)) {
                // responseCode 00 nghĩa là VNPay tìm thấy và xác nhận giao dịch (query
                // thành công), còn transactionStatus khác 00 nghĩa là giao dịch đó thật
                // sự thất bại/hủy — không phải do query lỗi. Đánh dấu FAILED để khách
                // biết cần thanh toán lại thay vì Payment kẹt ở PENDING mãi.
                savePaymentToDatabase(txnRef, "FAILED");
            }

            apiResponse.put("success", true);
            apiResponse.put("paid", paid);
            apiResponse.put("bookingId", bookingId);
            apiResponse.put("vnpayResponse", vnpResponse);

        } catch (Exception e) {
            response.setStatus(500);
            apiResponse.put("success", false);
            apiResponse.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(apiResponse));
    }

    private String sendPostRequest(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try ( DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            wr.flush();
        }
        StringBuilder resp = new StringBuilder();
        try ( BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                resp.append(inputLine);
            }
        }
        return resp.toString();
    }

    private void savePaymentToDatabase(String txnRef, String status) {
        // Match đúng theo TransactionRef (= vnp_TxnRef thật) thay vì chỉ BookingID,
        // tránh update nhầm các payment PENDING khác còn tồn đọng của cùng booking.
        // Chỉ set PaidAt khi thật sự thành công — set PaidAt cho giao dịch FAILED
        // sẽ gây hiểu nhầm là đã thanh toán dù chưa hề thanh toán được.
        String sql = "COMPLETED".equals(status)
                ? "UPDATE Payment SET Status = ?, PaidAt = GETDATE() WHERE TransactionRef = ? AND Status = 'PENDING' AND Method = 'VNPAY'"
                : "UPDATE Payment SET Status = ? WHERE TransactionRef = ? AND Status = 'PENDING' AND Method = 'VNPAY'";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, txnRef);
            int rows = ps.executeUpdate();
            System.out.println(">> VNPay IPN: Đã cập nhật " + rows + " dòng cho giao dịch " + txnRef);
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
