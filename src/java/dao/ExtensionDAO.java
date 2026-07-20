/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import utils.DbUtils;

/**
 *
 * @author asus
 */
public class ExtensionDAO {

    private Map<String, Object> rsToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columns; ++i) {
            row.put(md.getColumnName(i), rs.getObject(i));
        }
        return row;
    }

    public int getAccountIdByEmail(String email) throws Exception {
        String sql = "SELECT AccountID FROM Account WHERE Email = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    public int getCustomerIdByEmail(String email) throws Exception {
        String sql = "SELECT c.CustomerID FROM Customer c JOIN Account a ON c.AccountID = a.AccountID WHERE a.Email = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    public void createVoucher(String code, String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minBookingValue, Integer applicableVehicleTypeId, Integer maxUsagePerUser, Timestamp validFrom, Timestamp validTo, int createdBy) throws Exception {
        createVoucher(code, discountType, discountValue, maxDiscountAmount, minBookingValue, applicableVehicleTypeId, maxUsagePerUser, validFrom, validTo, createdBy, null, null);
    }

    public void createVoucher(String code, String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minBookingValue, Integer applicableVehicleTypeId, Integer maxUsagePerUser, Timestamp validFrom, Timestamp validTo, int createdBy, Integer campaignId) throws Exception {
        createVoucher(code, discountType, discountValue, maxDiscountAmount, minBookingValue, applicableVehicleTypeId, maxUsagePerUser, validFrom, validTo, createdBy, campaignId, null);
    }

    /** totalQuantity = tổng số suất voucher available (null = không giới hạn số lượng). */
    public void createVoucher(String code, String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minBookingValue, Integer applicableVehicleTypeId, Integer maxUsagePerUser, Timestamp validFrom, Timestamp validTo, int createdBy, Integer campaignId, Integer totalQuantity) throws Exception {
        String sql = "INSERT INTO Voucher (Code, DiscountType, DiscountValue, MaxDiscountAmount, MinBookingValue, ApplicableVehicleTypeID, MaxUsagePerUser, ValidFrom, ValidTo, Status, CreatedBy, CampaignID, TotalQuantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, discountType);
            ps.setBigDecimal(3, discountValue);
            ps.setBigDecimal(4, maxDiscountAmount);
            ps.setBigDecimal(5, minBookingValue);
            if (applicableVehicleTypeId != null) {
                ps.setInt(6, applicableVehicleTypeId);
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            if (maxUsagePerUser != null) {
                ps.setInt(7, maxUsagePerUser);
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setTimestamp(8, validFrom);
            ps.setTimestamp(9, validTo);
            ps.setInt(10, createdBy);
            if (campaignId != null) {
                ps.setInt(11, campaignId);
            } else {
                ps.setNull(11, Types.INTEGER);
            }
            if (totalQuantity != null) {
                ps.setInt(12, totalQuantity);
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Trả về CampaignID của campaign "khách inactive 30 ngày" — tạo mới nếu chưa có,
     * tái sử dụng nếu đã tồn tại (idempotent, tránh tạo trùng mỗi lần scheduler chạy).
     */
    public int getOrCreateComebackCampaign(int systemAccountId) throws Exception {
        String campaignName = "Khách hàng quay lại sau 30 ngày";
        String selectSql = "SELECT CampaignID FROM MarketingCampaign WHERE Name = ?";
        String insertSql = "INSERT INTO MarketingCampaign (Name, TriggerCondition, EmailTemplate, Status, CreatedBy, CreatedAt) VALUES (?, ?, ?, 'ACTIVE', ?, GETDATE())";
        try ( Connection conn = DbUtils.getConnection()) {
            try ( PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, campaignName);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("CampaignID");
                    }
                }
            }
            try ( PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, campaignName);
                ps.setString(2, "customer_inactive_30_days");
                ps.setString(3, "comeback_voucher");
                ps.setInt(4, systemAccountId);
                ps.executeUpdate();
                try ( ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    public void touchCampaignLastRun(int campaignId) throws Exception {
        String sql = "UPDATE MarketingCampaign SET LastRunAt = GETDATE() WHERE CampaignID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, campaignId);
            ps.executeUpdate();
        }
    }

    /**
     * Trả về code voucher ACTIVE còn hạn của campaign này — tạo mới nếu chưa có
     * hoặc voucher cũ đã hết hạn (rolling: 1 voucher dùng chung cho cả đợt campaign).
     */
    public String getOrCreateComebackVoucherCode(int campaignId, int systemAccountId) throws Exception {
        String selectSql = "SELECT TOP 1 Code FROM Voucher WHERE CampaignID = ? AND Status = 'ACTIVE' AND ValidTo >= GETDATE() ORDER BY VoucherID DESC";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setInt(1, campaignId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Code");
                }
            }
        }
        String code = "COMEBACK" + new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp expiry = new Timestamp(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000);
        createVoucher(code, "PERCENT", BigDecimal.valueOf(10), BigDecimal.valueOf(50000), null, null, 1, now, expiry, systemAccountId, campaignId);
        return code;
    }

    public List<Map<String, Object>> getVouchers(String status) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM Voucher";
        if (status != null && !status.trim().isEmpty()) {
            sql += " WHERE Status = ?";
        }
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null && !status.trim().isEmpty()) {
                ps.setString(1, status);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rsToMap(rs));
            }
        }
        return list;
    }

    public Map<String, Object> getVoucherById(int id) throws Exception {
        String sql = "SELECT * FROM Voucher WHERE VoucherID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rsToMap(rs);
            }
        }
        return null;
    }

    public void updateVoucher(int id, Timestamp validTo, String status) throws Exception {
        updateVoucher(id, validTo, status, null);
    }

    // Lưu ý: do dùng COALESCE, truyền totalQuantity=null nghĩa là "giữ nguyên giá trị cũ",
    // không phải "xóa về không giới hạn" — không có cách set về unlimited qua hàm này.
    public void updateVoucher(int id, Timestamp validTo, String status, Integer totalQuantity) throws Exception {
        String sql = "UPDATE Voucher SET ValidTo = COALESCE(?, ValidTo), Status = COALESCE(?, Status), "
                + "TotalQuantity = COALESCE(?, TotalQuantity) WHERE VoucherID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, validTo);
            ps.setString(2, status);
            if (totalQuantity != null) {
                ps.setInt(3, totalQuantity);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public void deleteVoucher(int id) throws Exception {
        String sql = "UPDATE Voucher SET Status = 'INACTIVE' WHERE VoucherID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public Map<String, Object> getInvoiceByBookingId(int bookingId) throws Exception {
        String sql = "SELECT * FROM Invoice WHERE BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rsToMap(rs);
            }
        }
        return null;
    }

    /**
     * Lịch sử giao dịch thật của khách hàng (cọc, thanh toán cuối chuyến, hoàn tiền),
     * lấy từ bảng Payment — KHÔNG dùng CustomerWallet (bảng đó chỉ dùng để tính nợ,
     * xem CustomerLockDAO). Alias cột trùng tên với format cũ (TransactionID,
     * TransactionType, Amount, BookingID, CreatedAt) để không phải sửa FE.
     */
    public List<Map<String, Object>> getWalletHistory(int customerId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        // PaymentSubType giữ nguyên giá trị gốc (DEPOSIT/FINAL/REFUND...) để FE phân
        // loại chi tiết hơn "Thanh toán cước xe" chung chung — TransactionType giữ
        // nguyên 2 giá trị PAYMENT/REFUND cũ để không phá vỡ chỗ đang dùng.
        String sql = "SELECT p.PaymentID AS TransactionID, "
                + "CASE WHEN p.PaymentType = 'REFUND' THEN 'REFUND' ELSE 'PAYMENT' END AS TransactionType, "
                + "p.PaymentType AS PaymentSubType, "
                + "p.Amount AS Amount, p.BookingID AS BookingID, p.PaidAt AS CreatedAt "
                + "FROM Payment p JOIN Booking bk ON bk.BookingID = p.BookingID "
                + "WHERE bk.CustomerID = ? AND p.Status = 'COMPLETED' "
                + "ORDER BY p.PaidAt DESC";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rsToMap(rs));
            }
        }
        return list;
    }

    public List<Map<String, Object>> getNotifications(int accountId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM Notification WHERE RecipientAccountID = ? ORDER BY CreatedAt DESC";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rsToMap(rs));
            }
        }
        return list;
    }

    public void markNotificationRead(int notificationId, int accountId) throws Exception {
        String sql = "UPDATE Notification SET IsRead = 1 WHERE NotificationID = ? AND RecipientAccountID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    /**
     * Tạo 1 Notification mới — dùng cho cảnh báo nợ xấu, thông báo khóa/mở tài
     * khoản, v.v. bookingId truyền null nếu thông báo không gắn với booking cụ
     * thể nào.
     */
    public void createNotification(int recipientAccountId, Integer bookingId, String title,
            String message, String type, String channel) throws Exception {
        String sql = "INSERT INTO Notification "
                + "(RecipientAccountID, BookingID, Title, Message, Type, Channel, IsRead, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0, ?)";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recipientAccountId);
            if (bookingId != null) {
                ps.setInt(2, bookingId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setString(5, type);
            ps.setString(6, channel);
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Cập nhật Payment khi cổng thanh toán báo giao dịch thành công (VNPay IPN,
     * MoMo callback, ...). Status LUÔN chuẩn hoá về 'COMPLETED' — dùng chung 1
     * giá trị duy nhất cho mọi cổng để tránh lệch dữ liệu (trước đây MoMo tự ghi
     * 'SUCCESS' trong khi VNPay ghi 'COMPLETED', khiến các query như
     * isDepositPaid/refundDeposit phải dò cả 2 giá trị).
     */
    public void processSuccessfulPayment(int paymentId, String transId, String payType) throws Exception {
        String sql = "UPDATE Payment SET Status = 'COMPLETED', TransactionRef = ?, Method = ?, PaidAt = ? WHERE PaymentID = ?";

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, transId);
            ps.setString(2, payType);
            ps.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setInt(4, paymentId);

            ps.executeUpdate();
        }
    }

    // ========================================================================
    // 5 method dưới đây đã chuyển sang service.PaymentService (chủ sở hữu duy
    // nhất của logic tiền: server tự tính amount, tái sử dụng row PENDING,
    // remainingOf cộng lại REFUND, chống double-pay). Comment lại (không xóa)
    // vì đây là code teammate vừa sửa song song trên master — giữ để đối
    // chiếu, KHÔNG dùng nữa. (createPayment cũng đã xóa — hết nơi gọi sau khi Momo bị gỡ.)
    // ========================================================================
    //
    // /**
    //  * Chỉ tính Payment đã thực sự COMPLETED (tiền đã về công ty). Trước đây
    //  * SUM(Amount) không lọc Status, nên các giao dịch VNPay/MoMo bị bỏ ngang
    //  * (kẹt PENDING) hoặc REFUND vẫn bị cộng vào "đã trả", khiến số tiền còn
    //  * lại tính SAI (thấp hơn thực tế) — tài xế thu thiếu tiền của khách.
    //  * PaymentType chỉ tính DEPOSIT/FINAL, không tính REFUND (tiền công ty trả
    //  * ra, không phải khách trả vào).
    //  */
    // public BigDecimal calculateFinalPayment(int bookingId) throws Exception {
    //     String sql = "SELECT " +
    //                  "(SELECT COALESCE(EstimatedTotal, 0) FROM BookingPricing WHERE BookingID = ?) - " +
    //                  "(SELECT COALESCE(SUM(Amount), 0) FROM Payment WHERE BookingID = ? " +
    //                  "AND Status = 'COMPLETED' AND PaymentType IN ('DEPOSIT', 'FINAL')) AS Remaining";
    //     try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
    //         ps.setInt(1, bookingId);
    //         ps.setInt(2, bookingId);
    //         try ( ResultSet rs = ps.executeQuery()) {
    //             if (rs.next()) {
    //                 return rs.getBigDecimal("Remaining");
    //             }
    //         }
    //     }
    //     return BigDecimal.ZERO;
    // }
    //
    // /**
    //  * Ghi nhận thanh toán phần còn lại (FINAL) — dùng cho nhánh CASH của
    //  * FinalPaymentController (chuyển khoản VNPay/MoMo đi qua processSuccessfulPayment
    //  * ở trên, không qua đây). Status chuẩn hoá 'COMPLETED', đồng bộ với mọi
    //  * cổng thanh toán khác thay vì tự ghi 'SUCCESS' riêng.
    //  */
    // public boolean processFinalPayment(int bookingId, String paymentMethod, BigDecimal amount) throws Exception {
    //     String sql = "INSERT INTO Payment (BookingID, PaymentType, Amount, Method, Status, PaidAt, TransactionRef) VALUES (?, 'FINAL', ?, ?, 'COMPLETED', GETDATE(), ?)";
    //
    //     try (Connection conn = DbUtils.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setInt(1, bookingId);
    //         ps.setBigDecimal(2, amount);
    //         ps.setString(3, paymentMethod);
    //         ps.setString(4, "TXN-F-" + bookingId);
    //         return ps.executeUpdate() > 0;
    //     }
    // }
    //
    // public boolean createPendingPayment(int bookingId, String paymentType, String method, double amount) {
    //     // Tạo TransactionRef nội bộ (dùng khi chưa có mã giao dịch thật từ cổng thanh toán,
    //     // vd. placeholder deposit được tạo tự động lúc booking CONFIRMED)
    //     String prefix = paymentType.equalsIgnoreCase("DEPOSIT") ? "D" : "F";
    //     String txnRef = "TXN-" + prefix + "-" + bookingId + "-" + System.currentTimeMillis();
    //     return createPendingPayment(bookingId, paymentType, method, amount, txnRef);
    // }
    //
    // /**
    //  * Overload cho phép truyền thẳng TransactionRef thật (vd. vnp_TxnRef gửi sang VNPay)
    //  * để về sau IPN/QueryDR có thể match update đúng chính xác 1 giao dịch bằng TransactionRef,
    //  * thay vì đoán mò theo BookingID (dễ đụng nhầm các payment PENDING khác của cùng booking).
    //  */
    // public boolean createPendingPayment(int bookingId, String paymentType, String method, double amount, String txnRef) {
    //     // Câu lệnh SQL (PaidAt để trống vì chưa thanh toán)
    //     String sql = "INSERT INTO Payment (BookingID, PaymentType, Method, Amount, Status, TransactionRef) " +
    //                  "VALUES (?, ?, ?, ?, 'PENDING', ?)";
    //
    //     try (Connection conn = DbUtils.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setInt(1, bookingId);
    //         ps.setString(2, paymentType); // "DEPOSIT" hoặc "FINAL"
    //         ps.setString(3, method);      // "VNPAY", "MOMO", "CASH"...
    //         ps.setDouble(4, amount);
    //         ps.setString(5, txnRef);
    //
    //         int rowsAffected = ps.executeUpdate();
    //         return rowsAffected > 0;
    //
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         return false;
    //     }
    // }
    //
    // /**
    //  * Hoàn cọc khi booking bị hủy mà khách không có lỗi (dispatcher reject
    //  * UNASSIGNED / customer free-cancel >=12h). Chỉ hoàn nếu có Payment
    //  * DEPOSIT COMPLETED cho booking này, nếu không có thì bỏ qua (trả về 0).
    //  *
    //  * Dùng chung 1 Connection với transaction của caller để đảm bảo atomic
    //  * với việc update Booking/Cancellation status — KHÔNG tự mở connection
    //  * riêng, KHÔNG tự commit/rollback (caller lo việc đó).
    //  *
    //  * Ghi 2 phía:
    //  * - Payment: insert thêm 1 row PaymentType='REFUND' (tiền công ty trả ra
    //  *   — Payment table đóng vai trò sổ cái công ty, khỏi cần bảng AdminWallet
    //  *   riêng: SUM(DEPOSIT/FINAL) = tiền vào, SUM(REFUND) = tiền ra).
    //  * - CustomerWallet: insert +Amount, TransactionType='REFUND' (phía khách).
    //  *
    //  * Chỉ còn dò Status = 'COMPLETED' — mọi cổng thanh toán giờ đã ghi thống
    //  * nhất 1 giá trị (xem processSuccessfulPayment/processFinalPayment), nên
    //  * không cần IN ('COMPLETED','SUCCESS') nữa. Vẫn giữ PaymentType IN
    //  * ('DEPOSIT','FINAL') vì đây là bug thật khác: FE cũ có lúc không gửi
    //  * paymentType nên cọc bị ghi nhầm thành FINAL.
    //  */
    // public BigDecimal refundDeposit(Connection conn, int bookingId, int customerId, String reason) throws Exception {
    //     BigDecimal depositAmount = null;
    //     String depositMethod = "VNPAY";
    //
    //     String findSql = "SELECT TOP 1 Amount, Method FROM Payment "
    //             + "WHERE BookingID = ? AND PaymentType IN ('DEPOSIT', 'FINAL') "
    //             + "AND Status = 'COMPLETED' "
    //             + "ORDER BY CASE WHEN PaymentType = 'DEPOSIT' THEN 0 ELSE 1 END, PaymentID";
    //     try (PreparedStatement ps = conn.prepareStatement(findSql)) {
    //         ps.setInt(1, bookingId);
    //         try (ResultSet rs = ps.executeQuery()) {
    //             if (rs.next()) {
    //                 depositAmount = rs.getBigDecimal("Amount");
    //                 depositMethod = rs.getString("Method");
    //             }
    //         }
    //     }
    //
    //     if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
    //         return BigDecimal.ZERO;
    //     }
    //
    //     Timestamp now = new Timestamp(System.currentTimeMillis());
    //     String txnRef = "TXN-R-" + bookingId + "-" + System.currentTimeMillis();
    //
    //     String insertPayment = "INSERT INTO Payment "
    //             + "(BookingID, PaymentType, Method, Amount, Status, TransactionRef, PaidAt) "
    //             + "VALUES (?, 'REFUND', ?, ?, 'COMPLETED', ?, ?)";
    //     try (PreparedStatement ps = conn.prepareStatement(insertPayment)) {
    //         ps.setInt(1, bookingId);
    //         ps.setString(2, depositMethod);
    //         ps.setBigDecimal(3, depositAmount);
    //         ps.setString(4, txnRef);
    //         ps.setTimestamp(5, now);
    //         ps.executeUpdate();
    //     }
    //
    //     String insertWallet = "INSERT INTO CustomerWallet "
    //             + "(CustomerID, Amount, TransactionType, BookingID, CreatedAt) "
    //             + "VALUES (?, ?, 'REFUND', ?, ?)";
    //     try (PreparedStatement ps = conn.prepareStatement(insertWallet)) {
    //         ps.setInt(1, customerId);
    //         ps.setBigDecimal(2, depositAmount);
    //         ps.setInt(3, bookingId);
    //         ps.setTimestamp(4, now);
    //         ps.executeUpdate();
    //     }
    //
    //     return depositAmount;
    // }
    //
    // /**
    //  * Kiểm tra booking đã đóng cọc chưa.
    //  * Dùng trong startTrip để block nếu khách chưa thanh toán.
    //  *
    //  * Chỉ còn dò Status = 'COMPLETED' (lý do xem ghi chú ở refundDeposit).
    //  * Vẫn giữ PaymentType IN ('DEPOSIT','FINAL') vì FE cũ từng không gửi
    //  * paymentType nên cọc bị ghi nhầm thành FINAL — khách trả đủ tiền trước
    //  * thì hiển nhiên đã đủ điều kiện cọc.
    //  */
    // public boolean isDepositPaid(int bookingId) throws Exception {
    //     String sql = "SELECT COUNT(*) FROM Payment "
    //             + "WHERE BookingID = ? AND PaymentType IN ('DEPOSIT', 'FINAL') "
    //             + "AND Status = 'COMPLETED'";
    //     try (java.sql.Connection conn = utils.DbUtils.getConnection();
    //          java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
    //         ps.setInt(1, bookingId);
    //         try (java.sql.ResultSet rs = ps.executeQuery()) {
    //             return rs.next() && rs.getInt(1) > 0;
    //         }
    //     }
    // }

    /**
     * Kiểm tra booking đã có Payment DEPOSIT (PENDING hoặc COMPLETED) chưa —
     * dùng để tránh tạo cọc trùng nếu logic "driver accept" bị gọi lại lần 2
     * (double-click, retry mạng, v.v). Chấp nhận cả PaymentType='FINAL' do
     * bug FE cũ không gửi paymentType (cùng lý do với isDepositPaid).
     */
    public boolean hasExistingDeposit(int bookingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM Payment "
                + "WHERE BookingID = ? AND PaymentType IN ('DEPOSIT', 'FINAL') "
                + "AND Status IN ('PENDING', 'COMPLETED')";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public int getCustomerAccountIdByBookingId(int bookingId) throws Exception {
        String sql = "SELECT a.AccountID FROM Booking b "
                + "JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.BookingID = ?";
        try (java.sql.Connection conn = utils.DbUtils.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("AccountID") : -1;
            }
        }
    }

    /**
     * Lấy tên khách hàng (FullName) theo BookingID — dùng để hiển thị trong
     * notification gửi cho dispatcher/driver, KHÔNG dùng "Bạn" (vì "Bạn" chỉ
     * đúng khi notification gửi thẳng cho chính khách đó).
     */
    public String getCustomerNameByBookingId(int bookingId) throws Exception {
        String sql = "SELECT a.FullName FROM Booking b "
                + "JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.BookingID = ?";
        try (java.sql.Connection conn = utils.DbUtils.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("FullName") : null;
            }
        }
    }

    public int getDriverAccountIdByBookingId(int bookingId) throws Exception {
        // Lấy driver đã ACCEPTED hoặc đang PENDING — để notify kể cả khi driver chưa phản hồi
        String sql = "SELECT TOP 1 a.AccountID FROM DriverJobBroadcast djb "
                + "JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "JOIN Account a ON a.AccountID = d.AccountID "
                + "WHERE djb.BookingID = ? AND djb.Status IN ('ACCEPTED', 'PENDING') "
                + "ORDER BY djb.DispatchedAt DESC";
        try (java.sql.Connection conn = utils.DbUtils.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("AccountID") : -1;
            }
        }
    }

    /**
     * Tra BookingID tu 1 PaymentID — dung cho IPN/callback (VNPay, MoMo) de
     * biet giao dich thanh toan nay thuoc booking nao ma gui notification.
     */
    public int getBookingIdByPaymentId(int paymentId) throws Exception {
        String sql = "SELECT BookingID FROM Payment WHERE PaymentID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("BookingID") : -1;
            }
        }
    }

    /**
     * Tra PaymentType ('DEPOSIT'/'FINAL') tu 1 PaymentID — dung de phan biet
     * IPN nay la xac nhan coc hay xac nhan thanh toan phan con lai, tranh gui
     * nham notification "khach da chuyen xong" cho truong hop dat coc.
     */
    public String getPaymentTypeById(int paymentId) throws Exception {
        String sql = "SELECT PaymentType FROM Payment WHERE PaymentID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("PaymentType") : null;
            }
        }
    }

    /**
     * Notify khach + tai xe + dispatcher khi thanh toan phan con lai (FINAL)
     * qua chuyen khoan (VNPay/MoMo) da duoc xac nhan thanh cong qua IPN/callback.
     * Dung chung cho ca 2 cong thanh toan de khong lap logic.
     */
    public void notifyFinalPaymentSuccess(int bookingId, BigDecimal amount, String methodLabel) {
        try {
            int customerAccountId = getCustomerAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                createNotification(customerAccountId, bookingId,
                        "Thanh toán thành công",
                        "Bạn đã chuyển khoản " + methodLabel + " thành công " + amount.toPlainString()
                                + "đ cho booking #" + bookingId + ". Cảm ơn!",
                        "PAYMENT_TRANSFER_CONFIRMED", "IN_APP");
            }
            int driverAccountId = getDriverAccountIdByBookingId(bookingId);
            if (driverAccountId != -1) {
                createNotification(driverAccountId, bookingId,
                        "Khách đã chuyển khoản rồi nè",
                        "Chuyến #" + bookingId + ": khách đã chuyển khoản (" + methodLabel + ") thành công "
                                + amount.toPlainString() + "đ. Khỏi cần thu tiền mặt nha, xong nhiệm vụ rồi!",
                        "PAYMENT_TRANSFER_CONFIRMED", "IN_APP");
            }
            java.util.List<Integer> dispatcherIds = new AccountDAO().getActiveDispatcherAccountIds();
            for (int dispId : dispatcherIds) {
                createNotification(dispId, bookingId,
                        "Booking #" + bookingId + " đã thanh toán chuyển khoản",
                        "Khách đã chuyển khoản " + amount.toPlainString() + "đ (" + methodLabel
                                + ") cho booking #" + bookingId + ".",
                        "PAYMENT_TRANSFER_CONFIRMED", "IN_APP");
            }
        } catch (Exception notifEx) {
            notifEx.printStackTrace();
        }
    }
}