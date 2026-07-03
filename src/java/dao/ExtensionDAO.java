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
        String sql = "INSERT INTO Voucher (Code, DiscountType, DiscountValue, MaxDiscountAmount, MinBookingValue, ApplicableVehicleTypeID, MaxUsagePerUser, ValidFrom, ValidTo, Status, CreatedBy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";
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
            ps.executeUpdate();
        }
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
        String sql = "UPDATE Voucher SET ValidTo = COALESCE(?, ValidTo), Status = COALESCE(?, Status) WHERE VoucherID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, validTo);
            ps.setString(2, status);
            ps.setInt(3, id);
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

    public List<Map<String, Object>> getWalletHistory(int customerId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM CustomerWallet WHERE CustomerID = ? ORDER BY CreatedAt DESC";
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

    public int createPayment(int bookingId, String paymentType, BigDecimal amount, String method) throws Exception {
        String sql = "INSERT INTO Payment (BookingID, PaymentType, Method, Amount, Status) VALUES (?, ?, ?, ?, 'PENDING')";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setString(2, paymentType);
            ps.setString(3, method);
            ps.setBigDecimal(4, amount);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public boolean createPendingPayment(int bookingId, String paymentType, String method, long amount) throws Exception {
        String sql = "INSERT INTO Payment (BookingID, PaymentType, Method, Amount, Status) VALUES (?, ?, ?, ?, 'PENDING')";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setString(2, paymentType);
            ps.setString(3, method);
            ps.setBigDecimal(4, java.math.BigDecimal.valueOf(amount));
            return ps.executeUpdate() > 0;
        }
    }

    public int createPayment(int bookingId, String paymentType, BigDecimal amount) throws Exception {
        String sql = "INSERT INTO Payment (BookingID, PaymentType, Method, Amount, Status) VALUES (?, ?, 'MOMO', ?, 'PENDING')";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setString(2, paymentType);
            ps.setBigDecimal(3, amount);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
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

    public void processSuccessfulPayment(int paymentId, String transId, String payType) throws Exception {
        String sql = "UPDATE Payment SET Status = 'SUCCESS', TransactionRef = ?, Method = ?, PaidAt = ? WHERE PaymentID = ?";

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, transId);
            ps.setString(2, payType);
            ps.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setInt(4, paymentId);

            ps.executeUpdate();
        }
    }

    public BigDecimal calculateFinalPayment(int bookingId) throws Exception {
        String sql = "SELECT (TotalAmount - DepositAmount) AS Remaining FROM Booking WHERE BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("Remaining");
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public boolean processFinalPayment(int bookingId, String paymentMethod, BigDecimal amount) throws Exception {
        String sql = "INSERT INTO Payment (BookingID, PaymentType, Amount, Method, Status, PaidAt) VALUES (?, 'FINAL', ?, ?, 'SUCCESS', GETDATE())";
        
        try (Connection conn = DbUtils.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            ps.setInt(1, bookingId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, paymentMethod);
            return ps.executeUpdate() > 0;
        }
    }

    public int getCustomerAccountIdByBookingId(int bookingId) throws Exception {
        String sql = "SELECT a.AccountID FROM Booking b "
                + "JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return -1;
            }
        }
    }

    public int getDriverAccountIdByBookingId(int bookingId) throws Exception {
        String sql = "SELECT a.AccountID FROM DriverJobBroadcast djb "
                + "JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "JOIN Account a ON a.AccountID = d.AccountID "
                + "WHERE djb.BookingID = ? AND djb.Status = 'ACCEPTED'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return -1;
            }
        }
    }

    public boolean updateBookingStatus(int bookingId, String status) throws Exception {
        String sql = "UPDATE Booking SET Status = ? WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, bookingId);
            return ps.executeUpdate() > 0;
        }
    }
}