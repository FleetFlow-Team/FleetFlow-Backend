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
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/v1/payments/vnpay/*")
public class VNPayController extends HttpServlet {

    private final Gson gson = new Gson();
    private final ExtensionDAO paymentDAO = new ExtensionDAO();

    private static final String VNP_TMN_CODE = "5GH8KI9L";
    private static final String VNP_HASH_SECRET = "SZ2DWD9030XB1RB85D6J4CBTGD4SJWFZ";
    private static final String VNP_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

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

            // Tạo giao dịch PENDING trong Database trước, lấy PaymentID để đối soát chính xác ở bước IPN
            int paymentId = paymentDAO.createPayment(bookingId, paymentType, BigDecimal.valueOf(amount), "VNPAY");

            if (paymentId == -1) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Lỗi tạo giao dịch trong hệ thống.");
                response.getWriter().print(gson.toJson(apiResponse));
                return; // Dừng luôn, không cho gọi sang VNPay nữa
            }

            String ipAddress = request.getHeader("X-FORWARDED-FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }

            // baseUrl suy ra từ chính request đang chạy, không hardcode port (tránh lệch môi trường/port)
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
            String vnpReturnUrl = baseUrl + "/api/v1/payments/vnpay/return";

            String vnp_TxnRef = paymentId + "_" + System.currentTimeMillis();

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
            vnp_Params.put("vnp_ReturnUrl", vnpReturnUrl);
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
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            Map<String, String> fields = extractVNPayParams(request);
            String vnp_SecureHash = request.getParameter("vnp_SecureHash");
            boolean isValid = verifySignature(fields, vnp_SecureHash);

            String vnp_TxnRef = request.getParameter("vnp_TxnRef");
            Integer paymentId = null;
            if (vnp_TxnRef != null && vnp_TxnRef.contains("_")) {
                paymentId = Integer.parseInt(vnp_TxnRef.split("_")[0]);
            }

            String transactionNo = request.getParameter("vnp_TransactionNo");
            String amountStr = request.getParameter("vnp_Amount");
            long amount = amountStr != null ? Long.parseLong(amountStr) / 100 : 0;

            boolean success = isValid && "00".equals(request.getParameter("vnp_ResponseCode"));
            Integer bookingId = paymentId != null ? getBookingIdByPaymentId(paymentId) : null;

            if (success && paymentId != null) {
                verifyAndSavePayment(paymentId, transactionNo, amount);
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Xác nhận thanh toán</title></head>");
            html.append("<body style='background:#f8fafc;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;'>");
            html.append("<div style='text-align:center;'>");
            html.append("<h3 style='color:#0f172a;'>Đang xác nhận thanh toán VNPay...</h3>");
            html.append("</div>");
            html.append("<script>");
            html.append("  const resultData = {");
            html.append("    type: 'VNPAY_RESULT',");
            html.append("    success: ").append(success).append(",");
            html.append("    bookingId: ").append(bookingId != null ? bookingId : "null").append(",");
            html.append("    amount: ").append(amount).append(",");
            html.append("    message: '").append(success ? "Thanh toán thành công" : "Giao dịch không thành công").append("'");
            html.append("  };");
            html.append("  if (window.opener && !window.opener.closed) {");
            html.append("      window.opener.postMessage(resultData, '*');");
            html.append("      setTimeout(() => window.close(), 500);");
            html.append("  } else {");
            html.append("      window.location.href = 'http://127.0.0.1:5500/pages/customer/tripHistory.html';");
            html.append("  }");
            html.append("</script>");
            html.append("</body></html>");

            out.print(html.toString());
        } catch (Exception e) {
            out.print("<html><body><h3>Lỗi xử lý Return: " + e.getMessage() + "</h3></body></html>");
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
                    int paymentId = Integer.parseInt(vnp_TxnRef.split("_")[0]);
                    String vnpTransactionNo = request.getParameter("vnp_TransactionNo");
                    long vnpAmountVnd = Long.parseLong(request.getParameter("vnp_Amount")) / 100;

                    String rspCode = verifyAndSavePayment(paymentId, vnpTransactionNo, vnpAmountVnd);
                    switch (rspCode) {
                        case "00":
                            result.addProperty("RspCode", "00");
                            result.addProperty("Message", "Confirm Success");
                            break;
                        case "01":
                            result.addProperty("RspCode", "01");
                            result.addProperty("Message", "Order not found");
                            break;
                        case "02":
                            result.addProperty("RspCode", "02");
                            result.addProperty("Message", "Order already confirmed");
                            break;
                        case "04":
                            result.addProperty("RspCode", "04");
                            result.addProperty("Message", "Invalid amount");
                            break;
                        default:
                            result.addProperty("RspCode", "99");
                            result.addProperty("Message", "Unknown Error");
                    }
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

    /**
     * Đối soát đúng theo PaymentID (không phải BookingID) để tránh cập nhật nhầm
     * khi 1 booking có nhiều lần thử thanh toán PENDING; đồng thời validate số tiền
     * VNPay trả về khớp với số tiền đã lưu lúc tạo giao dịch, và lưu lại vnp_TransactionNo.
     * Trả về mã VNPay-style: 00=OK, 01=không tìm thấy, 02=đã xử lý rồi, 04=sai số tiền, 99=lỗi khác.
     */
    private String verifyAndSavePayment(int paymentId, String vnpTransactionNo, long vnpAmountVnd) {
        String selectSql = "SELECT Amount, Status FROM Payment WHERE PaymentID = ?";
        String updateSql = "UPDATE Payment SET Status = 'COMPLETED', TransactionRef = ?, PaidAt = GETDATE() WHERE PaymentID = ? AND Status = 'PENDING'";
        try ( Connection conn = DbUtils.getConnection()) {
            BigDecimal storedAmount = null;
            String currentStatus = null;
            try ( PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, paymentId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "01";
                    }
                    storedAmount = rs.getBigDecimal("Amount");
                    currentStatus = rs.getString("Status");
                }
            }
            if (!"PENDING".equals(currentStatus)) {
                return "02";
            }
            if (storedAmount == null || storedAmount.longValue() != vnpAmountVnd) {
                return "04";
            }
            try ( PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, vnpTransactionNo);
                ps.setInt(2, paymentId);
                ps.executeUpdate();
            }
            System.out.println(">> VNPay IPN: Đã cập nhật thành công PaymentID " + paymentId);
            return "00";
        } catch (Exception e) {
            System.err.println("Lỗi cập nhật DB: " + e.getMessage());
            return "99";
        }
    }

    private Integer getBookingIdByPaymentId(int paymentId) {
        String sql = "SELECT BookingID FROM Payment WHERE PaymentID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("BookingID");
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi lấy BookingID: " + e.getMessage());
        }
        return null;
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
