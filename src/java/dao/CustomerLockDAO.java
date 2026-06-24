package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import utils.DbUtils;

public class CustomerLockDAO {

    /**
     * Ngưỡng nợ để tự động cảnh báo (không tự khóa) — đúng theo quyết định: nợ
     * >= 1,000,000đ
     */
    public static final BigDecimal DEBT_WARNING_THRESHOLD = new BigDecimal("1000000");

    /**
     * Tính nợ hiện tại của customer = SUM(CustomerWalletLedger.Amount). Quy
     * ước: Amount âm = phạt/thanh toán (làm tăng nợ), Amount dương = nạp/hoàn
     * tiền (giảm nợ). Trả về số âm nếu đang nợ, 0 hoặc dương nếu không nợ.
     */
    public BigDecimal getCurrentDebt(int customerId) throws Exception {
        String sql = "SELECT SUM(Amount) AS Total FROM CustomerWalletLedger WHERE CustomerID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal total = rs.getBigDecimal("Total");
                    return total != null ? total : BigDecimal.ZERO;
                }
                return BigDecimal.ZERO;
            }
        }
    }

    /**
     * Lấy AccountID từ CustomerID — dùng để update Account.Status và gửi
     * Notification.
     */
    public int getAccountIdByCustomerId(int customerId) throws Exception {
        String sql = "SELECT AccountID FROM Customer WHERE CustomerID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                throw new IllegalArgumentException("Không tìm thấy customer #" + customerId);
            }
        }
    }

    /**
     * Khóa tài khoản — Admin chủ động bấm, KHÔNG tự động. Set Account.Status =
     * 'LOCKED'. Booking controller sẽ check status này để chặn customer bị khóa
     * không tạo được booking mới.
     */
    public void lockAccount(int accountId) throws Exception {
        String sql = "UPDATE Account SET Status = 'LOCKED', UpdatedAt = ? WHERE AccountID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, accountId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Không tìm thấy account #" + accountId);
            }
        }
    }

    /**
     * Mở khóa tài khoản — chỉ Admin gọi sau khi xác nhận khách đã thanh toán
     * hết nợ. Set Account.Status = 'ACTIVE'.
     */
    public void unlockAccount(int accountId) throws Exception {
        String sql = "UPDATE Account SET Status = 'ACTIVE', UpdatedAt = ? WHERE AccountID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, accountId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Không tìm thấy account #" + accountId);
            }
        }
    }

    /**
     * Kiểm tra Account đang LOCKED hay không — dùng ở BookingController để chặn
     * tạo booking mới khi customer đang bị khóa.
     */
    public boolean isAccountLocked(int accountId) throws Exception {
        String sql = "SELECT Status FROM Account WHERE AccountID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "LOCKED".equalsIgnoreCase(rs.getString("Status"));
                }
                return false;
            }
        }
    }
}
