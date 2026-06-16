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
            conn.setAutoCommit(false);

            // 1. Insert Booking
            String sqlBooking = "INSERT INTO Booking "
                    + "(CustomerID, VehicleID, VoucherID, BookingType, TripDirection, Status, CreatedAt) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

            stmtBooking = conn.prepareStatement(sqlBooking, Statement.RETURN_GENERATED_KEYS);
            stmtBooking.setInt(1, booking.getCustomerId());
            stmtBooking.setInt(2, booking.getVehicleId());

            if (booking.getVoucherId() != 0) {
                stmtBooking.setInt(3, booking.getVoucherId());
            } else {
                stmtBooking.setNull(3, Types.BIGINT);
            }

            stmtBooking.setString(4, booking.getBookingType());
            stmtBooking.setString(5, booking.getTripDirection());
            stmtBooking.setString(6, "PENDING");
            stmtBooking.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            stmtBooking.executeUpdate();

            rs = stmtBooking.getGeneratedKeys();
            if (!rs.next()) {
                throw new Exception("Không lấy được BookingID sau khi insert");
            }
            int bookingId = rs.getInt(1);

            // 2. Insert BookingDetail (có DistanceKm)
            String sqlDetail = "INSERT INTO BookingDetail "
                    + "(BookingID, PickupAddress, PickupLat, PickupLng, "
                    + "DropoffAddress, DropoffLat, DropoffLng, DistanceKm, "
                    + "DepartureTime, ReturnTime) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            stmtDetail = conn.prepareStatement(sqlDetail);
            stmtDetail.setInt(1, bookingId);
            stmtDetail.setString(2, detail.getPickupAddress());
            stmtDetail.setBigDecimal(3, detail.getPickupLat());
            stmtDetail.setBigDecimal(4, detail.getPickupLng());
            stmtDetail.setString(5, detail.getDropoffAddress());
            stmtDetail.setBigDecimal(6, detail.getDropoffLat());
            stmtDetail.setBigDecimal(7, detail.getDropoffLng());
            stmtDetail.setBigDecimal(8, detail.getDistanceKm());  // thêm mới

            stmtDetail.setTimestamp(9, detail.getDepartureTime());

            if (detail.getReturnTime() != null) {
                stmtDetail.setTimestamp(10, detail.getReturnTime());
            } else {
                stmtDetail.setNull(10, Types.TIMESTAMP);
            }

            stmtDetail.executeUpdate();

            conn.commit();
            return bookingId;

        } catch (Exception e) {
            if (conn != null) conn.rollback();
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
    public Booking findById(int bookingId) throws Exception {
        String sql = "SELECT * FROM Booking WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookingId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return mapBooking(rs);
            return null;
        }
    }

    /**
     * Lấy BookingDetail theo BookingID
     */
    public BookingDetail findDetailByBookingId(int bookingId) throws Exception {
        String sql = "SELECT * FROM BookingDetail WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookingId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return mapBookingDetail(rs);
            return null;
        }
    }

    /**
     * Check xe có đang AVAILABLE không (BR-22)
     */
    public boolean isVehicleAvailable(int vehicleId) throws Exception {
        String sql = "SELECT Status FROM Vehicle WHERE VehicleID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, vehicleId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return "AVAILABLE".equalsIgnoreCase(rs.getString("Status"));
            }
            return false;
        }
    }

    /**
     * Check xe có bị trùng lịch không (BR-27)
     * Xe phải cách chuyến cũ ít nhất 60 phút
     */
    public boolean isVehicleScheduleConflict(int vehicleId, Timestamp departureTime) throws Exception {
        String sql = "SELECT bd.DepartureTime, bd.ReturnTime "
                + "FROM Booking b "
                + "JOIN BookingDetail bd ON b.BookingID = bd.BookingID "
                + "WHERE b.VehicleID = ? "
                + "AND b.Status NOT IN ('CANCELLED', 'COMPLETED') "
                + "AND bd.DepartureTime IS NOT NULL";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, vehicleId);
            ResultSet rs = stmt.executeQuery();

            long newDeparture = departureTime.getTime();
            long buffer = 60 * 60 * 1000L; // 60 phút tính bằng ms

            while (rs.next()) {
                Timestamp existingDeparture = rs.getTimestamp("DepartureTime");
                Timestamp existingReturn = rs.getTimestamp("ReturnTime");

                long existingEnd = existingReturn != null
                        ? existingReturn.getTime()
                        : existingDeparture.getTime() + (8 * 60 * 60 * 1000L); // ước tính 8h nếu không có ReturnTime

                // Conflict nếu chuyến mới bắt đầu trong vòng 60 phút sau chuyến cũ kết thúc
                if (newDeparture < existingEnd + buffer
                        && newDeparture > existingDeparture.getTime() - buffer) {
                    return true; // Có conflict
                }
            }
            return false; // Không conflict
        }
    }

    /**
     * Cập nhật trạng thái Booking
     */
    public void updateStatus(int bookingId, String status) throws Exception {
        String sql = "UPDATE Booking SET Status = ? WHERE BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, bookingId);
            stmt.executeUpdate();
        }
    }

    // ===================== MAPPING =====================

    private Booking mapBooking(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setId((int) rs.getInt("BookingID"));
        b.setCustomerId(rs.getInt("CustomerID"));
        b.setVehicleId(rs.getInt("VehicleID"));

        long voucherId = rs.getInt("VoucherID");
        if (!rs.wasNull()) b.setVoucherId((int) voucherId);

        b.setBookingType(rs.getString("BookingType"));
        b.setTripDirection(rs.getString("TripDirection"));
        b.setStatus(rs.getString("Status"));
        b.setCreatedAt(rs.getTimestamp("CreatedAt"));
        return b;
    }

    private BookingDetail mapBookingDetail(ResultSet rs) throws SQLException {
        BookingDetail d = new BookingDetail();
        d.setId((int) rs.getInt("DetailID"));
        d.setBookingId(rs.getInt("BookingID"));
        d.setPickupAddress(rs.getString("PickupAddress"));
        d.setPickupLat(rs.getBigDecimal("PickupLat"));
        d.setPickupLng(rs.getBigDecimal("PickupLng"));
        d.setDropoffAddress(rs.getString("DropoffAddress"));
        d.setDropoffLat(rs.getBigDecimal("DropoffLat"));
        d.setDropoffLng(rs.getBigDecimal("DropoffLng"));
        d.setDistanceKm(rs.getBigDecimal("DistanceKm"));  // thêm mới
        d.setDepartureTime(rs.getTimestamp("DepartureTime"));
        d.setReturnTime(rs.getTimestamp("ReturnTime"));
        return d;
    }
}