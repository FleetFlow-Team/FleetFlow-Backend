package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import utils.DbUtils;

public class AuditLogDAO {

    /**
     * Ghi 1 log hành động — dùng connection được truyền vào để gộp transaction
     * với hành động chính (ví dụ: update Booking.Status + ghi log cùng 1 transaction).
     */
    public void log(Connection conn, int accountId, String action, String entityName,
            String entityId, String oldValue, String newValue, String ipAddress) throws Exception {

        String sql = "INSERT INTO AuditLog "
                + "(AccountID, Action, EntityName, EntityID, OldValue, NewValue, IpAddress, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, action);
            ps.setString(3, entityName);
            ps.setString(4, entityId);
            ps.setString(5, oldValue);
            ps.setString(6, newValue);
            ps.setString(7, ipAddress);
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Overload tự mở/đóng connection riêng — dùng khi không cần gộp transaction.
     */
    public void log(int accountId, String action, String entityName,
            String entityId, String oldValue, String newValue, String ipAddress) throws Exception {
        try (Connection conn = DbUtils.getConnection()) {
            log(conn, accountId, action, entityName, entityId, oldValue, newValue, ipAddress);
        }
    }
}