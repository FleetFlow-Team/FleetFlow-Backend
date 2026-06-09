package dao;

import java.math.BigDecimal;
import java.sql.*;
import model.Booking;
import model.BookingDetail;
import utils.DbUtils;

public class BookingDAO {

    /**
     * Insert Booking + BookingDetail trong 1 transaction
     * Trả về BookingID vừa tạo
     */
    public long createBooking(Booking booking, BookingDetail detail) throws Exception {
        Connection conn = null;
        PreparedStatement stmtBooking = null;
        PreparedStatement stmtDetail = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false); // Bắt đầu transaction

            // 1. Insert Booking
            String sqlBooking = "INSERT INTO Booking "
                    + "(CustomerID, VehicleID, VoucherID, BookingType, TripDirection, Status, CreatedAt) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

            stmtBooking = conn.prepareStatement(sqlBooking, Statement.RETURN_GENERATED_KEYS);
            stmtBooking.setLong(1, booking.getCustomerId());
            stmtBooking.setLong(2, booking.getVehicleId());

            if (booking.getVoucherId() != null) {
                stmtBooking.setLong(3, booking.getVoucherId());
            } else {
                stmtBooking.setNull(3, Types.BIGINT);
            }

            stmtBooking.setString(4, booking.getBookingType());
            stmtBooking.setString(5, booking.getTripDirection());
            stmtBooking.setString(6, "PENDING");
            stmtBooking.setTimestamp(7, new Timestamp(System.currentTimeMillis()));

            stmtBooking.executeUpdate();

            // Lấy BookingID vừa insert
            rs = stmtBooking.getGeneratedKeys();
            if (!rs.next()) {
                throw new Exception("Không lấy được BookingID sau khi insert");
            }
            long bookingId = rs.getLong(1);

            // 2. Insert BookingDetail
            String sqlDetail = "INSERT INTO BookingDetail "
                    + "(BookingID, PickupAddress, PickupLat, PickupLng, "
                    + "DropoffAddress, DropoffLat, DropoffLng, DepartureTime, ReturnTime) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            stmtDetail = conn.prepareStatement(sqlDetail);
            stmtDetail.setLong(1, bookingId);
            stmtDetail.setString(2, detail.getPickupAddress());
            stmtDetail.setBigDecimal(3, detail.getPickupLat());
            stmtDetail.setBigDecimal(4, detail.getPickupLng());
            stmtDetail.setString(5, detail.getDropoffAddress());
            stmtDetail.setBigDecimal(6, detail.getDropoffLat());
            stmtDetail.setBigDecimal(7, detail.getDropoffLng());
            stmtDetail.setTimestamp(8, detail.getDepartureTime());

            if (detail.getReturnTime() != null) {
                stmtDetail.setTimestamp(9, detail.getReturnTime());
            } else {
                stmtDetail.setNull(9, Types.TIMESTAMP);
            }

            stmtDetail.executeUpdate();

            conn.commit(); // Commit transaction
            return bookingId;

        } catch (Exception e) {
            if (conn != null) {
                conn.rollback(); // Rollback nếu có lỗi
            }
            throw e;
        } finally {
            if (rs != null) rs.close();
            if (stmtBooking != null) stmtBooking.close();
            if (stmtDetail != null) stmtDetail.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Tìm Booking theo ID
     */
    public Booking findById(long bookingId) throws Exception {
        String sql = "SELECT * FROM Booking WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, bookingId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapBooking(rs);
            }
            return null;
        }
    }

    /**
     * Lấy BookingDetail theo BookingID
     */
    public BookingDetail findDetailByBookingId(long bookingId) throws Exception {
        String sql = "SELECT * FROM BookingDetail WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, bookingId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapBookingDetail(rs);
            }
            return null;
        }
    }

    /**
     * Cập nhật trạng thái Booking
     */
    public void updateStatus(long bookingId, String status) throws Exception {
        String sql = "UPDATE Booking SET Status = ? WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setLong(2, bookingId);
            stmt.executeUpdate();
        }
    }

    // ===================== MAPPING =====================

    private Booking mapBooking(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setId(rs.getLong("BookingID"));
        b.setCustomerId(rs.getLong("CustomerID"));
        b.setVehicleId(rs.getLong("VehicleID"));

        long voucherId = rs.getLong("VoucherID");
        if (!rs.wasNull()) b.setVoucherId(voucherId);

        b.setBookingType(rs.getString("BookingType"));
        b.setTripDirection(rs.getString("TripDirection"));
        b.setStatus(rs.getString("Status"));
        b.setCreatedAt(rs.getTimestamp("CreatedAt"));
        return b;
    }

    private BookingDetail mapBookingDetail(ResultSet rs) throws SQLException {
        BookingDetail d = new BookingDetail();
        d.setId(rs.getLong("DetailID"));
        d.setBookingId(rs.getLong("BookingID"));
        d.setPickupAddress(rs.getString("PickupAddress"));
        d.setPickupLat(rs.getBigDecimal("PickupLat"));
        d.setPickupLng(rs.getBigDecimal("PickupLng"));
        d.setDropoffAddress(rs.getString("DropoffAddress"));
        d.setDropoffLat(rs.getBigDecimal("DropoffLat"));
        d.setDropoffLng(rs.getBigDecimal("DropoffLng"));
        d.setDepartureTime(rs.getTimestamp("DepartureTime"));
        d.setReturnTime(rs.getTimestamp("ReturnTime"));
        return d;
    }
}