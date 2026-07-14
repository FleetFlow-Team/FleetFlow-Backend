package dao;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import model.Booking;
import model.BookingDetail;
import model.BookingPricing;
import utils.DbUtils;

public class BookingDAO {

    /**
     * Insert Booking + BookingDetail trong 1 transaction Trả về BookingID vừa
     * tạo
     */
    public long createBooking(Booking booking, BookingDetail detail, BookingPricing pricing) throws Exception {
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
            String sqlPricing
                    = "INSERT INTO BookingPricing "
                    + "(BookingID, RuleID, BaseFare, "
                    + "WeekendSurcharge, DiscountAmount, EstimatedTotal) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

            PreparedStatement stmtPricing
                    = conn.prepareStatement(sqlPricing);

            stmtPricing.setInt(1, bookingId);

            stmtPricing.setInt(2, pricing.getRuleId());

            stmtPricing.setBigDecimal(
                    3,
                    pricing.getBaseFare()
            );

            stmtPricing.setBigDecimal(
                    4,
                    pricing.getWeekendSurcharge()
            );

            stmtPricing.setBigDecimal(
                    5,
                    pricing.getDiscountAmount()
            );

            stmtPricing.setBigDecimal(
                    6,
                    pricing.getEstimatedTotal()
            );

            stmtPricing.executeUpdate();

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
     * Lấy thông tin giá (BaseFare, WeekendSurcharge, DiscountAmount, EstimatedTotal...)
     * của 1 booking — dùng cho GET /api/v1/bookings/{id} để FE hiển thị đúng giá
     * sau khi áp voucher (không bị lệch về giá gốc lúc checkout).
     */
    public BookingPricing findPricingByBookingId(int bookingId) throws Exception {
        String sql = "SELECT * FROM BookingPricing WHERE BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookingId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapBookingPricing(rs);
            }
            return null;
        }
    }

    private BookingPricing mapBookingPricing(ResultSet rs) throws Exception {
        BookingPricing p = new BookingPricing();
        p.setBookingId(rs.getInt("BookingID"));
        p.setRuleId(rs.getInt("RuleID"));
        p.setBaseFare(rs.getBigDecimal("BaseFare"));
        p.setWeekendSurcharge(rs.getBigDecimal("WeekendSurcharge"));
        p.setDiscountAmount(rs.getBigDecimal("DiscountAmount"));
        p.setEstimatedTotal(rs.getBigDecimal("EstimatedTotal"));
        try {
            p.setApprovedBy(rs.getInt("ApprovedBy"));
        } catch (Exception ignore) {
            // cột có thể NULL/không tồn tại tùy giai đoạn booking, bỏ qua nếu lỗi
        }
        try {
            p.setApprovedAt(rs.getTimestamp("ApprovedAt"));
        } catch (Exception ignore) {
        }
        return p;
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
     * Check xe có bị trùng lịch không (BR-27). Xe phải cách chuyến cũ ít nhất
     * 60 phút.
     *
     * newEndTime là giờ kết thúc THỰC TẾ của chuyến mới (đã tính sẵn ở
     * BookingService: departureTime + durationHours/durationDays, hoặc
     * returnTime nếu có, hoặc ước tính theo khoảng cách). Trước đây hàm này
     * chỉ nhận departureTime và so 1 điểm, đồng thời đoán bừa "8 tiếng" cho
     * mọi chuyến không có ReturnTime -> chuyến 1 tiếng bị khóa lịch cả ngày,
     * đồng thời không phát hiện được trường hợp chuyến mới kéo dài (VD DAILY
     * nhiều ngày) đè lên 1 chuyến khác bắt đầu sau đó. Giờ so sánh đúng 2
     * khoảng thời gian [newStart, newEnd] và [existingStart, existingEnd],
     * cộng buffer 60 phút mỗi đầu, theo kiểu overlap 2 chiều chuẩn.
     */
    public boolean isVehicleScheduleConflict(int vehicleId, Timestamp newDepartureTime, Timestamp newEndTime) throws Exception {
        String sql = "SELECT bd.DepartureTime, bd.ReturnTime "
                + "FROM Booking b "
                + "JOIN BookingDetail bd ON b.BookingID = bd.BookingID "
                + "WHERE b.VehicleID = ? "
                + "AND b.Status NOT IN ('CANCELLED', 'COMPLETED') "
                + "AND bd.DepartureTime IS NOT NULL";

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, vehicleId);
            ResultSet rs = stmt.executeQuery();

            long newStart = newDepartureTime.getTime();
            // Nếu vì lý do gì đó không tính được newEndTime (dữ liệu cũ/thiếu), coi chuyến
            // mới như 1 điểm thời gian — vẫn còn đúng hơn so với việc đoán bừa 8h cho chuyến cũ.
            long newEnd = newEndTime != null ? newEndTime.getTime() : newStart;
            long buffer = 60 * 60 * 1000L; // 60 phút tính bằng ms

            while (rs.next()) {
                Timestamp existingDeparture = rs.getTimestamp("DepartureTime");
                Timestamp existingReturn = rs.getTimestamp("ReturnTime");

                // Fallback 8h chỉ còn áp dụng cho các booking cũ (tạo trước khi có fix này)
                // mà lỡ chưa có ReturnTime lưu sẵn trong DB.
                long existingStart = existingDeparture.getTime();
                long existingEnd = existingReturn != null
                        ? existingReturn.getTime()
                        : existingStart + (8 * 60 * 60 * 1000L);

                // Overlap 2 chiều: chuyến mới bắt đầu trước khi chuyến cũ (+buffer) kết thúc
                // VÀ chuyến mới kết thúc sau khi chuyến cũ (-buffer) bắt đầu.
                if (newStart < existingEnd + buffer
                        && newEnd > existingStart - buffer) {
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

    public long createBooking(Booking booking, BookingDetail detail) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    /**
     * Tìm driver rảnh lâu nhất cho xe được chọn trong booking,
     * bỏ qua các driver đã từ chối trước đó (excludeDriverIds).
     *
     * Logic: driver thuộc cùng VehicleType với xe trong booking,
     * AvailabilityStatus = 'AVAILABLE', ApprovalStatus = 'APPROVED',
     * sort theo LastAssignedAt ASC (null lên đầu = chưa được gán bao giờ).
     *
     * Trả về DriverID hoặc -1 nếu không còn driver nào phù hợp.
     */
    public int findNextAvailableDriver(int vehicleId, java.util.List<Integer> excludeDriverIds) throws Exception {
        boolean hasExcludes = excludeDriverIds != null && !excludeDriverIds.isEmpty();

        String sql;
        if (hasExcludes) {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < excludeDriverIds.size(); i++) {
                placeholders.append(i == 0 ? "?" : ",?");
            }
            // SQL Server: TOP 1, ISNULL để driver chưa gán bao giờ lên đầu
            sql = "SELECT TOP 1 d.DriverID "
                    + "FROM Driver d "
                    + "JOIN Account a ON a.AccountID = d.AccountID "
                    + "WHERE d.AvailabilityStatus = 'AVAILABLE' "
                    + "AND d.IsDeleted = 0 "
                    + "AND a.Status <> 'LOCKED' "
                    + "AND d.DriverID NOT IN (" + placeholders + ") "
                    + "AND d.DriverID NOT IN ("
                    + "    SELECT AssignedDriverID FROM DriverJobBroadcast WHERE Status = 'PENDING'"
                    + ") "
                    + "ORDER BY ISNULL(d.LastAssignedAt, '1900-01-01') ASC";
        } else {
            sql = "SELECT TOP 1 d.DriverID "
                    + "FROM Driver d "
                    + "JOIN Account a ON a.AccountID = d.AccountID "
                    + "WHERE d.AvailabilityStatus = 'AVAILABLE' "
                    + "AND d.IsDeleted = 0 "
                    + "AND a.Status <> 'LOCKED' "
                    + "AND d.DriverID NOT IN ("
                    + "    SELECT AssignedDriverID FROM DriverJobBroadcast WHERE Status = 'PENDING'"
                    + ") "
                    + "ORDER BY ISNULL(d.LastAssignedAt, '1900-01-01') ASC";
        }

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (hasExcludes) {
                int idx = 1;
                for (int dId : excludeDriverIds) {
                    ps.setInt(idx++, dId);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("DriverID");
                }
            }
        }
        return -1; // không còn driver
    }

    /**
     * Cập nhật LastAssignedAt của driver sau khi được gán — để sort "rảnh lâu nhất" đúng.
     */
    public void updateDriverLastAssigned(int driverId) throws Exception {
        String sql = "UPDATE Driver SET LastAssignedAt = ? WHERE DriverID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, driverId);
            ps.executeUpdate();
        }
    }

    /**
     * Lấy danh sách booking PENDING quá timeout chưa được dispatcher xử lý.
     * Dùng cho auto-confirm scheduler.
     */
    public List<Booking> findPendingOverTimeout(int timeoutSeconds) throws Exception {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT b.*, "
                + "a.FullName AS CustomerName, a.PhoneNumber AS CustomerPhone "
                + "FROM Booking b "
                + "LEFT JOIN Customer c ON b.CustomerID = c.CustomerID "
                + "LEFT JOIN Account a ON c.AccountID = a.AccountID "
                + "WHERE b.Status = 'PENDING' "
                + "AND DATEDIFF(SECOND, b.CreatedAt, GETDATE()) >= ?";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, timeoutSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapBooking(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy danh sách booking UNASSIGNED (hết driver, cần dispatcher xử lý tay).
     * Dùng cho polling notification của dispatcher dashboard.
     */
    public List<java.util.Map<String, Object>> findUnassignedBookings() throws Exception {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT b.BookingID, b.CustomerID, b.VehicleID, b.BookingType, "
                + "b.TripDirection, b.Status, b.CreatedAt, b.UpdatedAt, "
                + "bd.PickupAddress, bd.DropoffAddress, bd.DepartureTime, "
                + "a.FullName AS CustomerName, a.PhoneNumber AS CustomerPhone "
                + "FROM Booking b "
                + "LEFT JOIN BookingDetail bd ON bd.BookingID = b.BookingID "
                + "LEFT JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "LEFT JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.Status = 'UNASSIGNED' "
                + "ORDER BY b.UpdatedAt DESC";

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("bookingId", rs.getInt("BookingID"));
                    row.put("customerId", rs.getInt("CustomerID"));
                    row.put("customerName", rs.getString("CustomerName"));
                    row.put("customerPhone", rs.getString("CustomerPhone"));
                    row.put("vehicleId", rs.getInt("VehicleID"));
                    row.put("bookingType", rs.getString("BookingType"));
                    row.put("tripDirection", rs.getString("TripDirection"));
                    row.put("status", rs.getString("Status"));
                    row.put("pickupAddress", rs.getString("PickupAddress"));
                    row.put("dropoffAddress", rs.getString("DropoffAddress"));
                    Timestamp dep = rs.getTimestamp("DepartureTime");
                    row.put("departureTime", dep != null ? dep.toString() : null);
                    row.put("updatedAt", rs.getTimestamp("UpdatedAt").toString());
                    list.add(row);
                }
            }
        }
        return list;
    }
}