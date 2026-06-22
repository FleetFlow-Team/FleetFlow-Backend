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
    public int respondToDispatch(int broadcastId, int driverId, String newStatus) throws Exception {
        String sql = "UPDATE DriverJobBroadcast "
                + "SET Status = ?, RespondedAt = ? "
                + "WHERE BroadcastID = ? AND AssignedDriverID = ? AND Status = 'PENDING'";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, broadcastId);
            ps.setInt(4, driverId);
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