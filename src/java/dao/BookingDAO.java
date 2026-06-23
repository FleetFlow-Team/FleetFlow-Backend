package dao;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import model.Booking;
import model.BookingDetail;
import utils.DbUtils;

public class BookingDAO {

    /**
     * Insert Booking + BookingDetail trong 1 transaction Trả về BookingID vừa
     * tạo
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

            // 2. Insert BookingDetail (PickupLat/Lng, DropoffLat/Lng có thể NULL khi HOURLY/DAILY)
            String sqlDetail = "INSERT INTO BookingDetail "
                    + "(BookingID, PickupAddress, PickupLat, PickupLng, "
                    + "DropoffAddress, DropoffLat, DropoffLng, DistanceKm, "
                    + "DepartureTime, ReturnTime, DurationHours, DurationDays, "
                    + "ReturnPickupAddress, ReturnPickupLat, ReturnPickupLng, "
                    + "ReturnDropoffAddress, ReturnDropoffLat, ReturnDropoffLng, ReturnDistanceKm) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            stmtDetail = conn.prepareStatement(sqlDetail);
            stmtDetail.setInt(1, bookingId);
            stmtDetail.setString(2, detail.getPickupAddress());

            if (detail.getPickupLat() != null) {
                stmtDetail.setBigDecimal(3, detail.getPickupLat());
            } else {
                stmtDetail.setNull(3, Types.DECIMAL);
            }
            if (detail.getPickupLng() != null) {
                stmtDetail.setBigDecimal(4, detail.getPickupLng());
            } else {
                stmtDetail.setNull(4, Types.DECIMAL);
            }

            stmtDetail.setString(5, detail.getDropoffAddress());

            if (detail.getDropoffLat() != null) {
                stmtDetail.setBigDecimal(6, detail.getDropoffLat());
            } else {
                stmtDetail.setNull(6, Types.DECIMAL);
            }
            if (detail.getDropoffLng() != null) {
                stmtDetail.setBigDecimal(7, detail.getDropoffLng());
            } else {
                stmtDetail.setNull(7, Types.DECIMAL);
            }
            if (detail.getDistanceKm() != null) {
                stmtDetail.setBigDecimal(8, detail.getDistanceKm());
            } else {
                stmtDetail.setNull(8, Types.DECIMAL);
            }

            stmtDetail.setTimestamp(9, detail.getDepartureTime());

            if (detail.getReturnTime() != null) {
                stmtDetail.setTimestamp(10, detail.getReturnTime());
            } else {
                stmtDetail.setNull(10, Types.TIMESTAMP);
            }

            if (detail.getDurationHours() != null) {
                stmtDetail.setInt(11, detail.getDurationHours());
            } else {
                stmtDetail.setNull(11, Types.INTEGER);
            }

            if (detail.getDurationDays() != null) {
                stmtDetail.setInt(12, detail.getDurationDays());
            } else {
                stmtDetail.setNull(12, Types.INTEGER);
            }

            // --- Cột 13-19: chiều về (NULL khi ONE_WAY) ---
            if (detail.getReturnPickupAddress() != null) {
                stmtDetail.setString(13, detail.getReturnPickupAddress());
            } else {
                stmtDetail.setNull(13, Types.NVARCHAR);
            }
            if (detail.getReturnPickupLat() != null) {
                stmtDetail.setBigDecimal(14, detail.getReturnPickupLat());
            } else {
                stmtDetail.setNull(14, Types.DECIMAL);
            }
            if (detail.getReturnPickupLng() != null) {
                stmtDetail.setBigDecimal(15, detail.getReturnPickupLng());
            } else {
                stmtDetail.setNull(15, Types.DECIMAL);
            }
            if (detail.getReturnDropoffAddress() != null) {
                stmtDetail.setString(16, detail.getReturnDropoffAddress());
            } else {
                stmtDetail.setNull(16, Types.NVARCHAR);
            }
            if (detail.getReturnDropoffLat() != null) {
                stmtDetail.setBigDecimal(17, detail.getReturnDropoffLat());
            } else {
                stmtDetail.setNull(17, Types.DECIMAL);
            }
            if (detail.getReturnDropoffLng() != null) {
                stmtDetail.setBigDecimal(18, detail.getReturnDropoffLng());
            } else {
                stmtDetail.setNull(18, Types.DECIMAL);
            }
            if (detail.getReturnDistanceKm() != null) {
                stmtDetail.setBigDecimal(19, detail.getReturnDistanceKm());
            } else {
                stmtDetail.setNull(19, Types.DECIMAL);
            }

            stmtDetail.executeUpdate();

            conn.commit();
            return bookingId;

        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmtBooking != null) {
                stmtBooking.close();
            }
            if (stmtDetail != null) {
                stmtDetail.close();
            }
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
        String sql
                = "SELECT b.*, "
                + "a.FullName AS CustomerName, "
                + "a.PhoneNumber AS CustomerPhone "
                + "FROM Booking b "
                + "LEFT JOIN Customer c ON b.CustomerID = c.CustomerID "
                + "LEFT JOIN Account a ON c.AccountID = a.AccountID "
                + "WHERE b.BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookingId);
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
    public BookingDetail findDetailByBookingId(int bookingId) throws Exception {
        String sql = "SELECT * FROM BookingDetail WHERE BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookingId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapBookingDetail(rs);
            }
            return null;
        }
    }

    /**
     * Check xe có đang AVAILABLE không (BR-22)
     */
    public boolean isVehicleAvailable(int vehicleId) throws Exception {
        String sql = "SELECT Status FROM Vehicle WHERE VehicleID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, vehicleId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return "AVAILABLE".equalsIgnoreCase(rs.getString("Status"));
            }
            return false;
        }
    }

    /**
     * Check xe có bị trùng lịch không (BR-27) Xe phải cách chuyến cũ ít nhất 60
     * phút
     */
    public boolean isVehicleScheduleConflict(int vehicleId, Timestamp departureTime) throws Exception {
        String sql = "SELECT bd.DepartureTime, bd.ReturnTime "
                + "FROM Booking b "
                + "JOIN BookingDetail bd ON b.BookingID = bd.BookingID "
                + "WHERE b.VehicleID = ? "
                + "AND b.Status NOT IN ('CANCELLED', 'COMPLETED') "
                + "AND bd.DepartureTime IS NOT NULL";

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement stmt = conn.prepareStatement(sql)) {

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
        try ( Connection conn = DbUtils.getConnection()) {
            updateStatus(conn, bookingId, status);
        }
    }

    /**
     * Overload dùng connection được truyền vào — để gộp transaction với việc
     * ghi AuditLog hoặc DriverJobBroadcast cùng lúc.
     */
    public void updateStatus(Connection conn, int bookingId, String status) throws SQLException {
        String sql = "UPDATE Booking SET Status = ?, UpdatedAt = ? WHERE BookingID = ?";
        try ( PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(3, bookingId);
            stmt.executeUpdate();
        }
    }

    // ===================== MAPPING =====================
    /**
     * Lấy danh sách Booking theo Status — dùng cho Dispatcher xem hàng chờ
     * duyệt (PENDING), hoặc Admin/Dispatcher xem theo trạng thái khác khi cần.
     * Kèm thông tin xe + customer cơ bản để hiển thị danh sách không cần gọi
     * thêm API.
     */
    public List<java.util.Map<String, Object>> findByStatusWithDetail(String status) throws Exception {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT b.BookingID, b.CustomerID, b.VehicleID, b.BookingType, "
                + "b.TripDirection, b.Status, b.Note, b.CreatedAt, "
                + "bd.PickupAddress, bd.DropoffAddress, bd.DepartureTime, "
                + "v.Brand, v.Model, v.LicensePlate, "
                + "a.FullName AS CustomerName, a.PhoneNumber AS CustomerPhone "
                + "FROM Booking b "
                + "LEFT JOIN BookingDetail bd ON bd.BookingID = b.BookingID "
                + "LEFT JOIN Vehicle v ON v.VehicleID = b.VehicleID "
                + "LEFT JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "LEFT JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.Status = ? "
                + "ORDER BY b.CreatedAt ASC";

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("bookingId", rs.getInt("BookingID"));
                    row.put("customerId", rs.getInt("CustomerID"));
                    row.put("customerName", rs.getString("CustomerName"));
                    row.put("customerPhone", rs.getString("CustomerPhone"));
                    row.put("vehicleId", rs.getInt("VehicleID"));
                    row.put("vehicleName", rs.getString("Brand") + " " + rs.getString("Model"));
                    row.put("licensePlate", rs.getString("LicensePlate"));
                    row.put("bookingType", rs.getString("BookingType"));
                    row.put("tripDirection", rs.getString("TripDirection"));
                    row.put("status", rs.getString("Status"));
                    row.put("note", rs.getString("Note"));
                    row.put("pickupAddress", rs.getString("PickupAddress"));
                    row.put("dropoffAddress", rs.getString("DropoffAddress"));
                    Timestamp dep = rs.getTimestamp("DepartureTime");
                    row.put("departureTime", dep != null ? dep.toString() : null);
                    row.put("createdAt", rs.getTimestamp("CreatedAt").toString());
                    list.add(row);
                }
            }
        }
        return list;
    }

    private Booking mapBooking(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setId((int) rs.getInt("BookingID"));
        b.setCustomerId(rs.getInt("CustomerID"));
        b.setCustomerName(rs.getString("CustomerName"));
        b.setCustomerPhone(rs.getString("CustomerPhone"));
        b.setVehicleId(rs.getInt("VehicleID"));

        long voucherId = rs.getInt("VoucherID");
        if (!rs.wasNull()) {
            b.setVoucherId((int) voucherId);
        }

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
        d.setDistanceKm(rs.getBigDecimal("DistanceKm"));
        d.setDepartureTime(rs.getTimestamp("DepartureTime"));
        d.setReturnTime(rs.getTimestamp("ReturnTime"));

        int durationHours = rs.getInt("DurationHours");
        if (!rs.wasNull()) {
            d.setDurationHours(durationHours);
        }

        int durationDays = rs.getInt("DurationDays");
        if (!rs.wasNull()) {
            d.setDurationDays(durationDays);
        }

        // --- Chiều về ---
        d.setReturnPickupAddress(rs.getString("ReturnPickupAddress"));
        d.setReturnPickupLat(rs.getBigDecimal("ReturnPickupLat"));
        d.setReturnPickupLng(rs.getBigDecimal("ReturnPickupLng"));
        d.setReturnDropoffAddress(rs.getString("ReturnDropoffAddress"));
        d.setReturnDropoffLat(rs.getBigDecimal("ReturnDropoffLat"));
        d.setReturnDropoffLng(rs.getBigDecimal("ReturnDropoffLng"));
        d.setReturnDistanceKm(rs.getBigDecimal("ReturnDistanceKm"));

        return d;
    }
}
