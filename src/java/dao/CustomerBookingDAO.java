package dao;
import model.Booking;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.sql.Timestamp;
import model.BookingDetail;
import model.Cancellation;
import model.PricingRule;
import model.Voucher;
import utils.DbUtils;


public class CustomerBookingDAO {

    // ===================== BE-23: Lịch sử đặt xe =====================

    // Inner class chứa đầy đủ thông tin booking + join
    public static class BookingRow {
        public int bookingId;
        public int customerId;
        public int vehicleId;
        public String bookingType;
        public String tripDirection;
        public String status;
        public Timestamp createdAt;
        public String pickupAddress;
        public String dropoffAddress;
        public Timestamp departureTime;
        public BigDecimal distanceKm;
        public String brand;
        public String model;
        public String licensePlate;
    }

    public List<BookingRow> getBookingsByCustomerId(int customerId) throws Exception {
        List<BookingRow> list = new ArrayList<>();
        String sql = "SELECT b.BookingID, b.CustomerID, b.VehicleID, "
                + "b.BookingType, b.TripDirection, b.Status, b.CreatedAt, "
                + "bd.PickupAddress, bd.DropoffAddress, bd.DepartureTime, bd.DistanceKm, "
                + "v.Brand, v.Model, v.LicensePlate "
                + "FROM Booking b "
                + "JOIN BookingDetail bd ON b.BookingID = bd.BookingID "
                + "JOIN Vehicle v ON b.VehicleID = v.VehicleID "
                + "WHERE b.CustomerID = ? "
                + "ORDER BY b.CreatedAt DESC";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BookingRow row = new BookingRow();
                row.bookingId = rs.getInt("BookingID");
                row.customerId = rs.getInt("CustomerID");
                row.vehicleId = rs.getInt("VehicleID");
                row.bookingType = rs.getString("BookingType");
                row.tripDirection = rs.getString("TripDirection");
                row.status = rs.getString("Status");
                row.createdAt = rs.getTimestamp("CreatedAt");
                row.pickupAddress = rs.getString("PickupAddress");
                row.dropoffAddress = rs.getString("DropoffAddress");
                row.departureTime = rs.getTimestamp("DepartureTime");
                row.distanceKm = rs.getBigDecimal("DistanceKm");
                row.brand = rs.getString("Brand");
                row.model = rs.getString("Model");
                row.licensePlate = rs.getString("LicensePlate");
                list.add(row);
            }
        }
        return list;
    }

    // ===================== BE-25: Cancel + tính phạt =====================

    public Booking findBookingById(int bookingId) throws Exception {
        String sql = "SELECT b.*, bd.DepartureTime FROM Booking b "
                + "JOIN BookingDetail bd ON b.BookingID = bd.BookingID "
                + "WHERE b.BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Booking b = new Booking();
                b.setId(rs.getInt("BookingID"));
                b.setCustomerId(rs.getInt("CustomerID"));
                b.setVehicleId(rs.getInt("VehicleID"));
                b.setBookingType(rs.getString("BookingType"));
                b.setTripDirection(rs.getString("TripDirection"));
                b.setStatus(rs.getString("Status"));
                b.setCreatedAt(rs.getTimestamp("CreatedAt"));
                b.setNote("departureTime=" + rs.getTimestamp("DepartureTime"));
                return b;
            }
            return null;
        }
    }

    public void cancelBookingWithPenalty(int bookingId, int customerId,
            int penaltyPercent, BigDecimal penaltyAmount, String reason) throws Exception {
        Connection conn = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            // 1. Update Booking status = CANCELLED
            PreparedStatement psBook = conn.prepareStatement(
                    "UPDATE Booking SET Status = 'CANCELLED' WHERE BookingID = ? AND CustomerID = ?");
            psBook.setInt(1, bookingId);
            psBook.setInt(2, customerId);
            int rows = psBook.executeUpdate();
            psBook.close();
            if (rows == 0) throw new IllegalArgumentException("Booking không tồn tại hoặc không thuộc customer này");

            // 2. Insert Cancellation
            PreparedStatement psCancel = conn.prepareStatement(
                    "INSERT INTO Cancellation (BookingID, PenaltyPercent, PenaltyAmount, PenaltyStatus, Reason, CancelledAt) "
                    + "VALUES (?, ?, ?, 'PENDING', ?, ?)");
            psCancel.setInt(1, bookingId);
            psCancel.setInt(2, penaltyPercent);
            psCancel.setBigDecimal(3, penaltyAmount);
            psCancel.setString(4, reason);
            psCancel.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            psCancel.executeUpdate();
            psCancel.close();

            conn.commit();
        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ===================== BE-26: Check price =====================

    public PricingRule getPricingRule(int vehicleId, String bookingType, String tripDirection) throws Exception {
        String sql = "SELECT pr.* FROM PricingRule pr "
                + "JOIN Vehicle v ON v.VehicleTypeID = pr.VehicleTypeID "
                + "WHERE v.VehicleID = ? AND pr.BookingType = ? AND pr.TripDirection = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, bookingType);
            ps.setString(3, tripDirection);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PricingRule rule = new PricingRule();
                rule.setId(rs.getInt("RuleID"));
                rule.setVehicleTypeId(rs.getInt("VehicleTypeID"));
                rule.setBookingType(rs.getString("BookingType"));
                rule.setTripDirection(rs.getString("TripDirection"));
                rule.setPricePerKm(rs.getBigDecimal("PricePerKm"));
                rule.setPricePerHour(rs.getBigDecimal("PricePerHour"));
                rule.setPricePerDay(rs.getBigDecimal("PricePerDay"));
                rule.setBasePrice(rs.getBigDecimal("BasePrice"));
                rule.setWeekendMultiplier(rs.getBigDecimal("WeekendMultiplier"));
                return rule;
            }
            return null;
        }
    }

    // ===================== BE-27: Apply Voucher =====================

    public Voucher findVoucherByCode(String code) throws Exception {
        String sql = "SELECT * FROM Voucher WHERE Code = ? AND Status = 'ACTIVE' "
                + "AND ValidFrom <= GETDATE() AND ValidTo >= GETDATE()";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Voucher v = new Voucher();
                v.setId(rs.getInt("VoucherID"));
                v.setCode(rs.getString("Code"));
                v.setDiscountType(rs.getString("DiscountType"));
                v.setDiscountValue(rs.getBigDecimal("DiscountValue"));
                v.setMaxDiscountAmount(rs.getBigDecimal("MaxDiscountAmount"));
                v.setMinBookingValue(rs.getBigDecimal("MinBookingValue"));
                v.setApplicableVehicleTypeId(rs.getInt("ApplicableVehicleTypeID"));
                v.setMaxUsagePerUser((Integer) rs.getObject("MaxUsagePerUser"));
                v.setValidFrom(rs.getTimestamp("ValidFrom"));
                v.setValidTo(rs.getTimestamp("ValidTo"));
                v.setStatus(rs.getString("Status"));
                return v;
            }
            return null;
        }
    }

    public int countVoucherUsageByCustomer(int voucherId, int customerId) throws Exception {
        String sql = "SELECT COUNT(*) FROM Booking "
                + "WHERE VoucherID = ? AND CustomerID = ? AND Status != 'CANCELLED'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, voucherId);
            ps.setInt(2, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }
}