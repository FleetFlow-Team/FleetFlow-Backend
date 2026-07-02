package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import model.DriverJobBroadcast;
import utils.DbUtils;

public class DriverJobBroadcastDAO {

    /**
     * Tạo 1 lệnh dispatch mới cho driver — dùng connection được truyền vào
     * để gộp transaction với việc update Booking.Status (nếu cần).
     * KHÔNG check unique BookingID — 1 booking có thể có nhiều broadcast
     * khi driver trước reject.
     */
    public long dispatchDriver(Connection conn, int bookingId, int driverId, int dispatchedBy) throws SQLException {
        String sql = "INSERT INTO DriverJobBroadcast "
                + "(BookingID, AssignedDriverID, DispatchedBy, Status, DispatchedAt) "
                + "VALUES (?, ?, ?, 'PENDING', ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, driverId);
            ps.setInt(3, dispatchedBy);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new SQLException("Không tạo được DriverJobBroadcast");
        }
    }

    /**
     * Overload tự mở/đóng connection riêng — dùng khi gọi độc lập, không cần transaction ngoài.
     */
    public long dispatchDriver(int bookingId, int driverId, int dispatchedBy) throws Exception {
        try (Connection conn = DbUtils.getConnection()) {
            return dispatchDriver(conn, bookingId, driverId, dispatchedBy);
        }
    }

    /**
     * Kiểm tra booking này có đang tồn tại broadcast PENDING không.
     * Dùng để chặn Dispatcher dispatch 2 lần cùng lúc cho 1 booking.
     */
    public boolean hasPendingBroadcast(int bookingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM DriverJobBroadcast WHERE BookingID = ? AND Status = 'PENDING'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }

    /**
     * Driver phản hồi lệnh dispatch — accept hoặc reject.
     * Trả về số dòng bị ảnh hưởng (0 nếu broadcast không tồn tại hoặc đã được xử lý trước đó).
     */
    /**
     * Driver phản hồi accept — không cần reason.
     */
    public int respondToDispatch(int broadcastId, int driverId, String newStatus) throws Exception {
        return respondToDispatch(broadcastId, driverId, newStatus, null);
    }

    /**
     * Driver phản hồi reject — có lưu reason vào DB.
     */
    public int respondToDispatch(int broadcastId, int driverId, String newStatus, String rejectReason) throws Exception {
        String sql = "UPDATE DriverJobBroadcast "
                + "SET Status = ?, RespondedAt = ?, RejectReason = ? "
                + "WHERE BroadcastID = ? AND AssignedDriverID = ? AND Status = 'PENDING'";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, rejectReason); // null nếu ACCEPT
            ps.setInt(4, broadcastId);
            ps.setInt(5, driverId);
            return ps.executeUpdate();
        }
    }

    /**
     * Source of truth duy nhất: lấy driver đã ACCEPT cho 1 booking.
     * Trả về -1 nếu chưa có driver nào accept.
     */
    public int getAcceptedDriverId(int bookingId) throws Exception {
        String sql = "SELECT AssignedDriverID FROM DriverJobBroadcast "
                + "WHERE BookingID = ? AND Status = 'ACCEPTED'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AssignedDriverID");
                }
                return -1;
            }
        }
    }

    /**
     * Lấy toàn bộ lịch sử broadcast của 1 booking (để Dispatcher xem ai đã reject trước đó).
     */
    public List<DriverJobBroadcast> getBroadcastHistory(int bookingId) throws Exception {
        List<DriverJobBroadcast> list = new ArrayList<>();
        String sql = "SELECT * FROM DriverJobBroadcast WHERE BookingID = ? ORDER BY DispatchedAt ASC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy danh sách lệnh dispatch đang PENDING của 1 driver (driver vào app thấy lệnh chờ phản hồi).
     */
    /**
     * Lấy danh sách lệnh PENDING của driver kèm đầy đủ thông tin booking
     * để driver biết đón ai, đi đâu, giờ nào mà không cần gọi thêm API.
     */
    public List<java.util.Map<String, Object>> getPendingForDriverWithDetail(int driverId) throws Exception {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT djb.BroadcastID, djb.BookingID, djb.DispatchedAt, "
                + "b.BookingType, b.TripDirection, b.Status AS BookingStatus, "
                + "bd.PickupAddress, bd.DropoffAddress, bd.DepartureTime, bd.DistanceKm, "
                + "a.FullName AS CustomerName, a.PhoneNumber AS CustomerPhone "
                + "FROM DriverJobBroadcast djb "
                + "JOIN Booking b ON b.BookingID = djb.BookingID "
                + "LEFT JOIN BookingDetail bd ON bd.BookingID = djb.BookingID "
                + "LEFT JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "LEFT JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE djb.AssignedDriverID = ? AND djb.Status = 'PENDING' "
                + "ORDER BY djb.DispatchedAt ASC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("broadcastId", rs.getInt("BroadcastID"));
                    row.put("bookingId", rs.getInt("BookingID"));
                    row.put("bookingType", rs.getString("BookingType"));
                    row.put("tripDirection", rs.getString("TripDirection"));
                    row.put("pickupAddress", rs.getString("PickupAddress"));
                    row.put("dropoffAddress", rs.getString("DropoffAddress"));
                    row.put("customerName", rs.getString("CustomerName"));
                    row.put("customerPhone", rs.getString("CustomerPhone"));
                    java.sql.Timestamp dep = rs.getTimestamp("DepartureTime");
                    row.put("departureTime", dep != null ? dep.toString() : null);
                    java.math.BigDecimal dist = rs.getBigDecimal("DistanceKm");
                    row.put("distanceKm", dist != null ? dist.toPlainString() : null);
                    row.put("dispatchedAt", rs.getTimestamp("DispatchedAt").toString());
                    list.add(row);
                }
            }
        }
        return list;
    }

    // Giữ lại method cũ cho backward compatibility
    public List<DriverJobBroadcast> getPendingForDriver(int driverId) throws Exception {
        List<DriverJobBroadcast> list = new ArrayList<>();
        String sql = "SELECT * FROM DriverJobBroadcast WHERE AssignedDriverID = ? AND Status = 'PENDING' "
                + "ORDER BY DispatchedAt ASC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy 1 broadcast theo BroadcastID — dùng khi cần biết BookingID gắn với broadcast đó.
     */
    public DriverJobBroadcast findById(int broadcastId) throws Exception {
        String sql = "SELECT * FROM DriverJobBroadcast WHERE BroadcastID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, broadcastId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    /**
     * Tạo notification cho driver khi được gán chuyến.
     * Dùng bảng Notification chung — RecipientAccountID là AccountID của driver.
     * Type = DISPATCH_ASSIGNED để FE filter đúng loại.
     */
    public void createDriverNotification(int driverId, int bookingId, String pickupAddress,
            String customerName, String departureTime) throws Exception {
        // Lấy AccountID từ DriverID
        String getAccountSql = "SELECT AccountID FROM Driver WHERE DriverID = ?";
        int accountId = -1;
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(getAccountSql)) {
            ps.setInt(1, driverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) accountId = rs.getInt("AccountID");
            }
        }
        if (accountId == -1) return;

        String title = "Chuyến mới được gán!";
        String message = "Khách: " + (customerName != null ? customerName : "N/A")
                + " | Đón tại: " + (pickupAddress != null ? pickupAddress : "N/A")
                + " | Giờ đi: " + (departureTime != null ? departureTime : "N/A");

        String sql = "INSERT INTO Notification "
                + "(RecipientAccountID, BookingID, Title, Message, Type, Channel, IsRead, CreatedAt) "
                + "VALUES (?, ?, ?, ?, 'DISPATCH_ASSIGNED', 'IN_APP', 0, ?)";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, bookingId);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Driver polling lấy notification chưa đọc.
     * Query từ bảng Notification chung, filter theo AccountID của driver + Type DISPATCH_*.
     */
    public List<java.util.Map<String, Object>> getUnreadNotifications(int driverId) throws Exception {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT n.NotificationID, n.BookingID, n.Title, n.Message, n.Type, n.IsRead, n.CreatedAt "
                + "FROM Notification n "
                + "JOIN Driver d ON d.AccountID = n.RecipientAccountID "
                + "WHERE d.DriverID = ? "
                + "AND n.IsRead = 0 "
                + "AND n.Type LIKE 'DISPATCH_%' "
                + "ORDER BY n.CreatedAt DESC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("notificationId", rs.getInt("NotificationID"));
                    row.put("bookingId", rs.getInt("BookingID"));
                    row.put("title", rs.getString("Title"));
                    row.put("message", rs.getString("Message"));
                    row.put("type", rs.getString("Type"));
                    row.put("isRead", rs.getBoolean("IsRead"));
                    row.put("createdAt", rs.getTimestamp("CreatedAt").toString());
                    list.add(row);
                }
            }
        }
        return list;
    }

    /**
     * Đánh dấu đã đọc tất cả notification DISPATCH của driver.
     */
    public void markNotificationsRead(int driverId) throws Exception {
        String sql = "UPDATE Notification SET IsRead = 1 "
                + "WHERE RecipientAccountID = (SELECT AccountID FROM Driver WHERE DriverID = ?) "
                + "AND IsRead = 0 AND Type LIKE 'DISPATCH_%'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            ps.executeUpdate();
        }
    }

    /**
     * Lịch sử chuyến đi của driver — tất cả booking driver đã ACCEPT (đã nhận
     * chuyến), bất kể trạng thái hiện tại (CONFIRMED, COMPLETED, CANCELLED...).
     * Hỗ trợ filter theo status booking nếu FE cần (vd chỉ xem COMPLETED).
     */
    public List<java.util.Map<String, Object>> getTripHistoryForDriver(int driverId, String statusFilter) throws Exception {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT djb.BroadcastID, djb.BookingID, djb.DispatchedAt, djb.RespondedAt, "
                + "b.BookingType, b.TripDirection, b.Status AS BookingStatus, "
                + "bd.PickupAddress, bd.DropoffAddress, bd.DepartureTime, bd.DistanceKm, "
                + "a.FullName AS CustomerName, a.PhoneNumber AS CustomerPhone, "
                + "bp.EstimatedTotal "
                + "FROM DriverJobBroadcast djb "
                + "JOIN Booking b ON b.BookingID = djb.BookingID "
                + "LEFT JOIN BookingDetail bd ON bd.BookingID = djb.BookingID "
                + "LEFT JOIN BookingPricing bp ON bp.BookingID = djb.BookingID "
                + "LEFT JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "LEFT JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE djb.AssignedDriverID = ? AND djb.Status = 'ACCEPTED' ");

        boolean hasFilter = statusFilter != null && !statusFilter.isEmpty();
        if (hasFilter) {
            sql.append("AND b.Status = ? ");
        }
        sql.append("ORDER BY djb.RespondedAt DESC");

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, driverId);
            if (hasFilter) {
                ps.setString(2, statusFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("broadcastId", rs.getInt("BroadcastID"));
                    row.put("bookingId", rs.getInt("BookingID"));
                    row.put("bookingType", rs.getString("BookingType"));
                    row.put("tripDirection", rs.getString("TripDirection"));
                    row.put("bookingStatus", rs.getString("BookingStatus"));
                    row.put("pickupAddress", rs.getString("PickupAddress"));
                    row.put("dropoffAddress", rs.getString("DropoffAddress"));
                    row.put("customerName", rs.getString("CustomerName"));
                    row.put("customerPhone", rs.getString("CustomerPhone"));
                    java.sql.Timestamp dep = rs.getTimestamp("DepartureTime");
                    row.put("departureTime", dep != null ? dep.toString() : null);
                    java.math.BigDecimal dist = rs.getBigDecimal("DistanceKm");
                    row.put("distanceKm", dist != null ? dist.toPlainString() : null);
                    java.math.BigDecimal total = rs.getBigDecimal("EstimatedTotal");
                    row.put("estimatedTotal", total != null ? total.toPlainString() : null);
                    row.put("acceptedAt", rs.getTimestamp("RespondedAt").toString());
                    list.add(row);
                }
            }
        }
        return list;
    }

    private DriverJobBroadcast mapRow(ResultSet rs) throws SQLException {
        DriverJobBroadcast b = new DriverJobBroadcast();
        b.setId(rs.getInt("BroadcastID"));
        b.setBookingId(rs.getInt("BookingID"));
        b.setAssignedDriverId(rs.getInt("AssignedDriverID"));
        b.setDispatchedBy(rs.getInt("DispatchedBy"));
        b.setStatus(rs.getString("Status"));
        b.setDispatchedAt(rs.getTimestamp("DispatchedAt"));
        b.setRespondedAt(rs.getTimestamp("RespondedAt"));
        return b;
    }
} 