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
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/v1/payments/vnpay/*")
public class VNPayController extends HttpServlet {

    private final Gson gson = new Gson();

    /**
     * P2-2: chỉ Customer đã đăng nhập mới được tạo yêu cầu thanh toán. Callback
     * /return, /ipn của VNPay KHÔNG áp guard này (xác thực bằng chữ ký vnp_SecureHash).
     * TODO: bổ sung kiểm ownership (customer sở hữu bookingId).
     */
    private boolean requireCustomer(HttpServletRequest request, HttpServletResponse response, Map<String, Object> apiResponse) throws IOException {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (token == null || !utils.JwtUtils.validateToken(token)) {
            response.setStatus(401);
            apiResponse.put("success", false);
            apiResponse.put("message", "Unauthorized");
            response.getWriter().print(gson.toJson(apiResponse));
            return false;
        }
        if (!"Customer".equalsIgnoreCase(utils.JwtUtils.getRoleFromToken(token))) {
            response.setStatus(403);
            apiResponse.put("success", false);
            apiResponse.put("message", "Forbidden");
            response.getWriter().print(gson.toJson(apiResponse));
            return false;
        }
        return true;
    }

    // Toàn bộ URL/key đọc từ /config/vnpay.properties — không hardcode theo môi trường
    private static final String VNP_TMN_CODE = utils.VNPayConfig.get("vnp.tmnCode", "");
    private static final String VNP_HASH_SECRET = utils.VNPayConfig.get("vnp.hashSecret", "");
    private static final String VNP_URL = utils.VNPayConfig.get("vnp.payUrl",
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    private static final String VNP_RETURN_URL = utils.VNPayConfig.get("vnp.returnUrl",
            "http://localhost:8080/FleetFlow/api/v1/payments/vnpay/return");
    private static final String VNP_IPN_URL = utils.VNPayConfig.get("vnp.ipnUrl",
            "http://localhost:8080/FleetFlow/api/v1/payments/vnpay/ipn");
    private static final String FE_RETURN_URL = utils.VNPayConfig.get("fe.returnUrl",
            "http://localhost:8080/payment-result");

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
        if (!requireCustomer(request, response, apiResponse)) {
            return;
        }

        try {

            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            int bookingId = body.get("bookingId").getAsInt();
            String paymentType = body.has("paymentType") ? body.get("paymentType").getAsString() : "FINAL"; // Hoặc DEPOSIT

            // Server tự tính số tiền — KHÔNG tin amount từ client, tránh khách
            // tự khai 1.000đ rồi được xác nhận thanh toán đủ
            ExtensionDAO paymentDAO = new ExtensionDAO();
            java.math.BigDecimal amountBD;
            if ("DEPOSIT".equalsIgnoreCase(paymentType)) {
                java.math.BigDecimal total = new dao.CustomerBookingDAO().getBookingTotalAmount(bookingId);
                amountBD = total.multiply(new java.math.BigDecimal("0.30"))
                        .setScale(0, java.math.RoundingMode.HALF_UP);
            } else {
                amountBD = paymentDAO.calculateFinalPayment(bookingId)
                        .setScale(0, java.math.RoundingMode.HALF_UP);
            }
            long amount = amountBD.longValueExact();
            if (amount <= 0) {
                response.setStatus(400);
                apiResponse.put("success", false);
                apiResponse.put("message", "Không có khoản tiền cần thanh toán cho booking này.");
                response.getWriter().print(gson.toJson(apiResponse));
                return;
            }

            String vnp_TxnRef = bookingId + "_" + System.currentTimeMillis();

// Tạo giao dịch PENDING trong Database trước (lưu TxnRef để callback update đúng dòng)
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
            if (ipAddress == null || ipAddress.contains(":")) {
                ipAddress = "127.0.0.1";
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
        String status = "failed";
        String rawTxnRef = request.getParameter("vnp_TxnRef");
        String bookingIdStr = rawTxnRef;
        String bankCode = request.getParameter("vnp_BankCode");
        String transactionNo = request.getParameter("vnp_TransactionNo");
        String amount = "0";
        try {
            Map<String, String> fields = extractVNPayParams(request);
            String vnp_SecureHash = request.getParameter("vnp_SecureHash");
            boolean isValid = verifySignature(fields, vnp_SecureHash);

            if (bookingIdStr != null && bookingIdStr.contains("_")) {
                bookingIdStr = bookingIdStr.split("_")[0];
            }
            String amountStr = request.getParameter("vnp_Amount");
            if (amountStr != null) {
                amount = String.valueOf(Long.parseLong(amountStr) / 100);
            }

            if (isValid && "00".equals(request.getParameter("vnp_ResponseCode"))) {
                status = "success";
                savePaymentToDatabase(rawTxnRef, Integer.parseInt(bookingIdStr), "COMPLETED");
            }
        } catch (Exception e) {
            // Phân biệt lỗi hệ thống với giao dịch bị từ chối: log lại để debug,
            // người dùng vẫn chỉ thấy status=failed
            System.err.println("Loi xu ly VNPay return (bookingId=" + bookingIdStr + "): " + e);
            status = "failed";
        }

        String redirect = FE_RETURN_URL
                + "?status=" + status
                + "&bookingId=" + safe(bookingIdStr)
                + "&amount=" + safe(amount)
                + "&bankCode=" + safe(bankCode)
                + "&transactionNo=" + safe(transactionNo);
        response.sendRedirect(redirect);
    }

    private String safe(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return "";
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
                    savePaymentToDatabase(vnp_TxnRef, bookingId, "COMPLETED");

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

    private void savePaymentToDatabase(String txnRef, int bookingId, String status) {
        // Tìm đúng payment theo TransactionRef (vnp_TxnRef) — không quét mọi dòng
        // PENDING của booking, tránh complete nhầm payment khác
        String findSql = "SELECT TOP 1 PaymentID, PaymentType, Amount FROM Payment "
                + "WHERE TransactionRef = ? AND Status = 'PENDING' AND Method = 'VNPAY'";
        String pSql = "UPDATE Payment SET Status = ?, PaidAt = GETDATE() WHERE PaymentID = ?";
        // Không complete booking đã CANCELLED — tránh IPN/return đến muộn lật ngược trạng thái hủy
        String bSql = "UPDATE Booking SET Status = 'COMPLETED' WHERE BookingID = ? AND Status <> 'CANCELLED'";
        Connection conn = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            int paymentId = -1;
            String paymentType = null;
            long paidAmount = 0L;
            try (PreparedStatement psFind = conn.prepareStatement(findSql)) {
                psFind.setString(1, txnRef);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        paymentId = rs.getInt("PaymentID");
                        paymentType = rs.getString("PaymentType");
                        java.math.BigDecimal amt = rs.getBigDecimal("Amount");
                        paidAmount = (amt == null) ? 0L : amt.longValue();
                    }
                }
            }

            if (paymentId == -1) {
                // Không có payment PENDING nào khớp TxnRef (đã xử lý trước đó,
                // hoặc giao dịch không hợp lệ) → không đụng vào Booking
                conn.rollback();
                System.out.println(">> VNPay: khong co Payment PENDING khop TxnRef " + txnRef + ", bo qua");
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(pSql)) {
                ps.setString(1, status);
                ps.setInt(2, paymentId);
                ps.executeUpdate();
            }

            // Chỉ thanh toán FINAL mới kết thúc booking — đặt cọc (DEPOSIT) xong
            // booking vẫn phải tiếp tục chạy
            if ("FINAL".equalsIgnoreCase(paymentType)) {
                try (PreparedStatement ps2 = conn.prepareStatement(bSql)) {
                    ps2.setInt(1, bookingId);
                    ps2.executeUpdate();
                }
            }

            conn.commit();
            System.out.println(">> VNPay: da xac nhan payment " + paymentId + " (" + paymentType
                    + ") cho booking " + bookingId);

            // Gửi notification SAU khi commit thành công. Lỗi noti không được
            // ảnh hưởng tới kết quả thanh toán → bọc try/catch riêng bên trong.
            notifyAfterPayment(bookingId, paymentType, paidAmount);
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ignore) {
                }
            }
            System.err.println("Loi cap nhat DB: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * Gửi notification khi thanh toán VNPay thành công — đồng bộ với luồng tiền
     * mặt ở FinalPaymentController. Mọi account tra từ bookingId (không hardcode).
     * DEPOSIT → báo customer + dispatcher (+ driver nếu đã gán): sẵn sàng điều phối.
     * FINAL   → báo customer + driver + dispatcher: đã thanh toán & hoàn tất chuyến.
     */
    private void notifyAfterPayment(int bookingId, String paymentType, long amount) {
        try {
            ExtensionDAO exDao = new ExtensionDAO();
            int customerAccId = exDao.getCustomerAccountIdByBookingId(bookingId);
            int driverAccId = exDao.getDriverAccountIdByBookingId(bookingId);
            List<Integer> dispatcherIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
            String amt = String.valueOf(amount);

            if ("DEPOSIT".equalsIgnoreCase(paymentType)) {
                if (customerAccId != -1) {
                    exDao.createNotification(customerAccId, bookingId, "Đặt cọc thành công",
                            "Bạn đã thanh toán cọc " + amt + "đ cho booking #" + bookingId
                            + " qua VNPay. Vui lòng chờ điều phối tài xế.",
                            "PAYMENT_DEPOSIT_CONFIRMED", "IN_APP");
                }
                for (int dispId : dispatcherIds) {
                    exDao.createNotification(dispId, bookingId, "Booking #" + bookingId + " đã đặt cọc",
                            "Khách đã thanh toán cọc " + amt + "đ cho booking #" + bookingId
                            + ", sẵn sàng điều phối tài xế.",
                            "PAYMENT_DEPOSIT_CONFIRMED", "IN_APP");
                }
                if (driverAccId != -1) {
                    exDao.createNotification(driverAccId, bookingId,
                            "Booking #" + bookingId + " đã được đặt cọc",
                            "Khách đã thanh toán cọc cho booking #" + bookingId + ".",
                            "PAYMENT_DEPOSIT_CONFIRMED", "IN_APP");
                }
            } else {
                if (customerAccId != -1) {
                    exDao.createNotification(customerAccId, bookingId, "Thanh toán hoàn tất",
                            "Bạn đã thanh toán " + amt + "đ cho booking #" + bookingId
                            + " qua VNPay. Chuyến đi đã hoàn tất, cảm ơn bạn!",
                            "PAYMENT_FINAL_CONFIRMED", "IN_APP");
                }
                if (driverAccId != -1) {
                    exDao.createNotification(driverAccId, bookingId, "Khách đã thanh toán VNPay",
                            "Booking #" + bookingId + " đã được thanh toán " + amt + "đ qua VNPay.",
                            "PAYMENT_FINAL_CONFIRMED", "IN_APP");
                }
                for (int dispId : dispatcherIds) {
                    exDao.createNotification(dispId, bookingId,
                            "Booking #" + bookingId + " đã thanh toán & hoàn tất",
                            "Khách đã thanh toán " + amt + "đ qua VNPay cho booking #" + bookingId + ".",
                            "PAYMENT_FINAL_CONFIRMED", "IN_APP");
                }
            }
        } catch (Exception e) {
            System.err.println("VNPay: loi gui notification cho booking " + bookingId + ": " + e.getMessage());
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