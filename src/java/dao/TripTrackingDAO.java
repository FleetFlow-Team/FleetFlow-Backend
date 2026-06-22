package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import model.TripEventLog;
import model.TripGpsLog;
import utils.DbUtils;

public class TripTrackingDAO {

    // ===================== GPS Log =====================

    /**
     * Ghi 1 điểm GPS mới — Driver đẩy tọa độ mỗi 30 giây trong suốt hành trình.
     */
    public void insertGpsLog(int bookingId, BigDecimal lat, BigDecimal lng) throws Exception {
        String sql = "INSERT INTO TripGpsLog (BookingID, Latitude, Longitude, RecordedAt) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setBigDecimal(2, lat);
            ps.setBigDecimal(3, lng);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Lấy điểm GPS MỚI NHẤT của 1 booking — dùng để Dispatcher xem vị trí hiện tại trên map.
     */
    public TripGpsLog getLatestGpsLog(int bookingId) throws Exception {
        String sql = "SELECT TOP 1 * FROM TripGpsLog WHERE BookingID = ? ORDER BY RecordedAt DESC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapGpsLog(rs);
                }
                return null;
            }
        }
    }

    /**
     * Lấy vị trí mới nhất của TẤT CẢ booking đang ONGOING — dùng cho Dispatcher map tổng quan.
     * Mỗi booking chỉ trả về 1 điểm GPS gần nhất (không phải toàn bộ lịch sử).
     */
    public List<TripGpsLog> getLatestGpsForOngoingBookings() throws Exception {
        List<TripGpsLog> list = new ArrayList<>();
        String sql = "SELECT g.* FROM TripGpsLog g "
                + "INNER JOIN ("
                + "    SELECT BookingID, MAX(RecordedAt) AS MaxRecordedAt "
                + "    FROM TripGpsLog "
                + "    GROUP BY BookingID"
                + ") latest ON g.BookingID = latest.BookingID AND g.RecordedAt = latest.MaxRecordedAt "
                + "INNER JOIN Booking b ON b.BookingID = g.BookingID "
                + "WHERE b.Status = 'ONGOING'";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapGpsLog(rs));
            }
        }
        return list;
    }

    private TripGpsLog mapGpsLog(ResultSet rs) throws SQLException {
        TripGpsLog log = new TripGpsLog();
        log.setId(rs.getLong("GpsLogID"));
        log.setBookingId(rs.getInt("BookingID"));
        log.setLatitude(rs.getBigDecimal("Latitude"));
        log.setLongitude(rs.getBigDecimal("Longitude"));
        log.setRecordedAt(rs.getTimestamp("RecordedAt"));
        return log;
    }

    // ===================== Trip Event Log =====================

    /**
     * Ghi 1 event vào lịch sử hành trình — dùng cho start/complete trip.
     * Nhận connection từ ngoài để gộp transaction với việc update Booking.Status.
     */
    public void insertEvent(Connection conn, int bookingId, String eventType, String notes) throws SQLException {
        String sql = "INSERT INTO TripEventLog (BookingID, EventType, Notes, EventTime) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setString(2, eventType);
            ps.setString(3, notes);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Lấy toàn bộ lịch sử event của 1 booking, sắp xếp theo thời gian.
     */
    public List<TripEventLog> getEventsByBookingId(int bookingId) throws Exception {
        List<TripEventLog> list = new ArrayList<>();
        String sql = "SELECT * FROM TripEventLog WHERE BookingID = ? ORDER BY EventTime ASC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEvent(rs));
                }
            }
        }
        return list;
    }

    private TripEventLog mapEvent(ResultSet rs) throws SQLException {
        TripEventLog event = new TripEventLog();
        event.setId(rs.getLong("EventLogID"));
        event.setBookingId(rs.getInt("BookingID"));
        event.setEventType(rs.getString("EventType"));
        event.setNotes(rs.getString("Notes"));
        event.setEventTime(rs.getTimestamp("EventTime"));
        return event;
    }
}