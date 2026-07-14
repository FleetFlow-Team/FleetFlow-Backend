package service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import utils.DbUtils;

/**
 * Chủ sở hữu DUY NHẤT của logic tiền trong hệ thống.
 *
 * Quy ước dữ liệu (sau migration 2026-07-12-payment-normalize.sql):
 *  - Payment.Status ∈ {PENDING, COMPLETED, FAILED} — không còn 'SUCCESS'.
 *  - Cọc luôn PaymentType='DEPOSIT', phần còn lại 'FINAL', hoàn 'REFUND'.
 *  - Mỗi (BookingID, PaymentType) tối đa 1 row PENDING
 *    (unique index UX_Payment_OnePendingPerType).
 *  - Amount luôn do server tính — không bao giờ nhận từ FE.
 */
public class PaymentService {

    /** Công thức cọc DUY NHẤT: 30% tổng tiền, làm tròn 0 lẻ HALF_UP. */
    public static BigDecimal depositAmountOf(BigDecimal estimatedTotal) {
        if (estimatedTotal == null) {
            return BigDecimal.ZERO;
        }
        return estimatedTotal.multiply(new BigDecimal("0.30"))
                .setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal depositAmountOf(int bookingId) throws Exception {
        return depositAmountOf(getEstimatedTotal(bookingId));
    }

    public BigDecimal getEstimatedTotal(int bookingId) throws Exception {
        String sql = "SELECT COALESCE(EstimatedTotal, 0) AS Total "
                + "FROM BookingPricing WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("Total") : BigDecimal.ZERO;
            }
        }
    }

    /**
     * Số còn nợ = EstimatedTotal − (DEPOSIT+FINAL đã COMPLETED)
     *           + (REFUND đã COMPLETED), chặn sàn 0.
     * REFUND cộng lại vì hoàn cọc là tiền ra — khách nợ lại phần đó.
     */
    public BigDecimal remainingOf(int bookingId) throws Exception {
        String sql = "SELECT "
                + "(SELECT COALESCE(EstimatedTotal, 0) FROM BookingPricing WHERE BookingID = ?) "
                + "- (SELECT COALESCE(SUM(Amount), 0) FROM Payment "
                + "   WHERE BookingID = ? AND PaymentType IN ('DEPOSIT','FINAL') AND Status = 'COMPLETED') "
                + "+ (SELECT COALESCE(SUM(Amount), 0) FROM Payment "
                + "   WHERE BookingID = ? AND PaymentType = 'REFUND' AND Status = 'COMPLETED') AS Remaining";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, bookingId);
            ps.setInt(3, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal remaining = rs.getBigDecimal("Remaining");
                    if (remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
                        return remaining;
                    }
                }
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Lấy row PENDING sẵn có của (booking, loại) — cập nhật Method/Amount —
     * hoặc tạo mới nếu chưa có. Trả về PaymentID.
     * Race 2 request song song: unique index chặn insert thứ hai → bắt
     * SQLException rồi select lại row thắng cuộc.
     */
    public int getOrCreatePending(int bookingId, String paymentType,
            String method, BigDecimal amount) throws Exception {
        try (Connection conn = DbUtils.getConnection()) {
            Integer existing = findPendingId(conn, bookingId, paymentType);
            if (existing != null) {
                String upd = "UPDATE Payment SET Method = ?, Amount = ? "
                        + "WHERE PaymentID = ? AND Status = 'PENDING'";
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setString(1, method);
                    ps.setBigDecimal(2, amount);
                    ps.setInt(3, existing);
                    ps.executeUpdate();
                }
                return existing;
            }

            String prefix = "DEPOSIT".equalsIgnoreCase(paymentType) ? "D" : "F";
            String txnRef = "TXN-" + prefix + "-" + bookingId + "-" + System.currentTimeMillis();
            String ins = "INSERT INTO Payment (BookingID, PaymentType, Method, Amount, Status, TransactionRef) "
                    + "VALUES (?, ?, ?, ?, 'PENDING', ?)";
            try (PreparedStatement ps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, bookingId);
                ps.setString(2, paymentType);
                ps.setString(3, method);
                ps.setBigDecimal(4, amount);
                ps.setString(5, txnRef);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException dup) {
                Integer raced = findPendingId(conn, bookingId, paymentType);
                if (raced != null) {
                    return raced;
                }
                throw dup;
            }
        }
        throw new IllegalStateException("Không tạo được Payment PENDING cho booking " + bookingId);
    }

    private Integer findPendingId(Connection conn, int bookingId, String paymentType)
            throws SQLException {
        String sql = "SELECT PaymentID FROM Payment "
                + "WHERE BookingID = ? AND PaymentType = ? AND Status = 'PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setString(2, paymentType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("PaymentID") : null;
            }
        }
    }

    /**
     * Xác nhận thanh toán từ cổng (VNPay return/IPN). Idempotent.
     * Trả mã VNPay-style: 00=OK, 01=không tìm thấy, 02=đã xử lý rồi,
     * 04=sai số tiền, 99=lỗi khác.
     */
    public String confirmPaid(int paymentId, String gatewayTxnNo, long paidAmountVnd) {
        String selectSql = "SELECT Amount, Status FROM Payment WHERE PaymentID = ?";
        String updateSql = "UPDATE Payment SET Status = 'COMPLETED', TransactionRef = ?, "
                + "PaidAt = GETDATE() WHERE PaymentID = ? AND Status = 'PENDING'";
        try (Connection conn = DbUtils.getConnection()) {
            BigDecimal storedAmount = null;
            String currentStatus = null;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, paymentId);
                try (ResultSet rs = ps.executeQuery()) {
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
            if (storedAmount == null || storedAmount.longValue() != paidAmountVnd) {
                return "04";
            }
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, gatewayTxnNo);
                ps.setInt(2, paymentId);
                ps.executeUpdate();
            }
            return "00";
        } catch (Exception e) {
            System.err.println("[PaymentService.confirmPaid] " + e.getMessage());
            return "99";
        }
    }

    /**
     * Còn giữ để tương thích ngược với các chỗ FE đang đọc field này — từ khi
     * settleCashFinal() tất toán ngay, sẽ không còn row FINAL/CASH/PENDING nào
     * nữa nên hàm này sẽ luôn trả false.
     */
    public boolean hasPendingCashFinal(int bookingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM Payment "
                + "WHERE BookingID = ? AND PaymentType = 'FINAL' AND Method = 'CASH' AND Status = 'PENDING'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Khách đã đóng cọc chưa — gate cho driver start trip. */
    public boolean isDepositPaid(int bookingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM Payment "
                + "WHERE BookingID = ? AND PaymentType = 'DEPOSIT' AND Status = 'COMPLETED'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Khách khai ý định trả FINAL bằng tiền mặt — tất toán NGAY, không chờ tài
     * xế xác nhận lại nữa (chỉ còn gửi thông báo nhắc tài xế thu tiền).
     * Nếu đã có row FINAL PENDING (vd từ 1 lần thử VNPay bỏ dở) thì tái sử
     * dụng luôn row đó thay vì tạo trùng, đổi Method sang CASH.
     */
    public BigDecimal settleCashFinal(int bookingId, BigDecimal amount) throws Exception {
        String txnRef = "TXN-CASH-" + bookingId + "-" + System.currentTimeMillis();
        try (Connection conn = DbUtils.getConnection()) {
            Integer existing = findPendingId(conn, bookingId, "FINAL");
            if (existing != null) {
                String upd = "UPDATE Payment SET Method = 'CASH', Amount = ?, Status = 'COMPLETED', "
                        + "TransactionRef = ?, PaidAt = GETDATE() WHERE PaymentID = ?";
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setBigDecimal(1, amount);
                    ps.setString(2, txnRef);
                    ps.setInt(3, existing);
                    ps.executeUpdate();
                }
                return amount;
            }

            String ins = "INSERT INTO Payment (BookingID, PaymentType, Method, Amount, Status, TransactionRef, PaidAt) "
                    + "VALUES (?, 'FINAL', 'CASH', ?, 'COMPLETED', ?, GETDATE())";
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                ps.setInt(1, bookingId);
                ps.setBigDecimal(2, amount);
                ps.setString(3, txnRef);
                ps.executeUpdate();
            }
            return amount;
        }
    }

    /**
     * Hoàn cọc khi hủy mà khách không có lỗi (dispatcher reject UNASSIGNED /
     * customer free-cancel). DÙNG CHUNG transaction với caller — không tự
     * mở/commit Connection. Trả về số tiền đã hoàn (0 nếu chưa từng cọc).
     */
    public BigDecimal refundDeposit(Connection conn, int bookingId, int customerId,
            String reason) throws Exception {
        BigDecimal depositAmount = null;
        String depositMethod = "VNPAY";

        String findSql = "SELECT TOP 1 Amount, Method FROM Payment "
                + "WHERE BookingID = ? AND PaymentType = 'DEPOSIT' AND Status = 'COMPLETED' "
                + "ORDER BY PaymentID";
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    depositAmount = rs.getBigDecimal("Amount");
                    depositMethod = rs.getString("Method");
                }
            }
        }

        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        String txnRef = "TXN-R-" + bookingId + "-" + System.currentTimeMillis();

        String insertPayment = "INSERT INTO Payment "
                + "(BookingID, PaymentType, Method, Amount, Status, TransactionRef, PaidAt) "
                + "VALUES (?, 'REFUND', ?, ?, 'COMPLETED', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertPayment)) {
            ps.setInt(1, bookingId);
            ps.setString(2, depositMethod);
            ps.setBigDecimal(3, depositAmount);
            ps.setString(4, txnRef);
            ps.setTimestamp(5, now);
            ps.executeUpdate();
        }

        String insertWallet = "INSERT INTO CustomerWallet "
                + "(CustomerID, Amount, TransactionType, BookingID, CreatedAt) "
                + "VALUES (?, ?, 'REFUND', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertWallet)) {
            ps.setInt(1, customerId);
            ps.setBigDecimal(2, depositAmount);
            ps.setInt(3, bookingId);
            ps.setTimestamp(4, now);
            ps.executeUpdate();
        }

        return depositAmount;
    }
}
