package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

public class AdminDashboardDAO {

    /**
     * Đếm số booking theo từng status trong khoảng thời gian (fromDate/toDate có thể null = không filter).
     * Dùng cho phần Summary của dashboard.
     */
    public Map<String, Integer> getStatusSummary(String fromDate, String toDate) throws Exception {
        Map<String, Integer> summary = new HashMap<>();

        StringBuilder sql = new StringBuilder(
                "SELECT Status, COUNT(*) AS Total FROM Booking WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND CreatedAt <= ? ");
            params.add(toDate);
        }
        sql.append("GROUP BY Status");

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, (String) params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    summary.put(rs.getString("Status"), rs.getInt("Total"));
                }
            }
        }

        // Đảm bảo luôn có đủ key dù count = 0, để FE không phải tự xử lý thiếu key
        String[] allStatuses = {"PENDING", "APPROVED", "REJECTED", "DISPATCHED",
                "CONFIRMED", "ONGOING", "COMPLETED", "CANCELLED"};
        for (String s : allStatuses) {
            summary.putIfAbsent(s, 0);
        }

        return summary;
    }

    /**
     * Tổng doanh thu (từ BookingPricing.EstimatedTotal) của các booking COMPLETED
     * trong khoảng thời gian — phần KPI tài chính của dashboard.
     */
    public java.math.BigDecimal getTotalRevenue(String fromDate, String toDate) throws Exception {
    StringBuilder sql = new StringBuilder(
            "SELECT SUM(p.Amount) AS Total FROM Payment p "
            + "JOIN Booking b ON b.BookingID = p.BookingID "
            + "WHERE p.Status IN ('SUCCESS', 'COMPLETED') "
            + "AND p.PaymentType IN ('DEPOSIT', 'FINAL') ");
    List<Object> params = new ArrayList<>();
    if (fromDate != null && !fromDate.isEmpty()) {
        sql.append("AND p.PaidAt >= ? ");
        params.add(fromDate);
    }
    if (toDate != null && !toDate.isEmpty()) {
        sql.append("AND p.PaidAt <= ? ");
        params.add(toDate);
    }
    try (Connection conn = DbUtils.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql.toString())) {
        for (int i = 0; i < params.size(); i++) {
            ps.setString(i + 1, (String) params.get(i));
        }
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                java.math.BigDecimal total = rs.getBigDecimal("Total");
                return total != null ? total : java.math.BigDecimal.ZERO;
            }
            return java.math.BigDecimal.ZERO;
        }
    }
}

    /**
     * Đếm số lệnh dispatch bị driver reject trong khoảng thời gian — KPI vận hành
     * (tỷ lệ driver reject cao cảnh báo vấn đề điều phối).
     */

    public int getDriverRejectCount(String fromDate, String toDate) throws Exception {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS Total FROM DriverJobBroadcast "
                + "WHERE Status = 'REJECTED' ");
        List<Object> params = new ArrayList<>();

        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND DispatchedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND DispatchedAt <= ? ");
            params.add(toDate);
        }

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, (String) params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("Total") : 0;
            }
        }
    }

    /**
     * Thống kê khách hàng cho KPI dashboard:
     * - totalCustomers: tổng số khách hàng đang hoạt động (không phụ thuộc bộ lọc ngày, vì đây là số lũy kế)
     * - newCustomersToday: số khách hàng đăng ký mới trong ngày hôm nay (theo giờ server)
     */
    public Map<String, Integer> getCustomerStats() throws Exception {
        Map<String, Integer> stats = new HashMap<>();

        String totalSql = "SELECT COUNT(*) AS Total FROM Customer c "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE c.IsDeleted = 0 AND a.IsDeleted = 0";

        String todaySql = "SELECT COUNT(*) AS Total FROM Customer c "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE c.IsDeleted = 0 AND a.IsDeleted = 0 "
                + "AND CAST(c.CreatedAt AS DATE) = CAST(GETDATE() AS DATE)";

        try (Connection conn = DbUtils.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(totalSql);
                 ResultSet rs = ps.executeQuery()) {
                stats.put("totalCustomers", rs.next() ? rs.getInt("Total") : 0);
            }
            try (PreparedStatement ps = conn.prepareStatement(todaySql);
                 ResultSet rs = ps.executeQuery()) {
                stats.put("newCustomersToday", rs.next() ? rs.getInt("Total") : 0);
            }
        }

        return stats;
    }

    /**
     * Chi tiết booking theo status cụ thể, kèm join thông tin liên quan:
     * - CANCELLED: join Cancellation (lý do, % phạt, tiền phạt)
     * - REJECTED: join AuditLog (lý do dispatcher từ chối)
     * - Các status khác: thông tin booking cơ bản + xe + customer
     */
    public List<Map<String, Object>> getBookingsByStatusDetailed(String status,
            String fromDate, String toDate) throws Exception {

        List<Map<String, Object>> list = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT b.BookingID, b.CustomerID, b.VehicleID, b.BookingType, "
                + "b.TripDirection, b.Status, b.Note, b.CreatedAt, "
                + "v.Brand, v.Model, v.LicensePlate, "
                + "a.FullName AS CustomerName, a.PhoneNumber AS CustomerPhone "
                + "FROM Booking b "
                + "LEFT JOIN Vehicle v ON v.VehicleID = b.VehicleID "
                + "LEFT JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "LEFT JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.Status = ? ");

        List<Object> params = new ArrayList<>();
        params.add(status);

        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND b.CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND b.CreatedAt <= ? ");
            params.add(toDate);
        }
        sql.append("ORDER BY b.CreatedAt DESC");

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, (String) params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    int bookingId = rs.getInt("BookingID");

                    row.put("bookingId", bookingId);
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
                    row.put("createdAt", rs.getTimestamp("CreatedAt").toString());

                    // Join thêm thông tin riêng theo từng status
                    if ("CANCELLED".equalsIgnoreCase(status)) {
                        attachCancellationInfo(conn, bookingId, row);
                    } else if ("REJECTED".equalsIgnoreCase(status)) {
                        attachAuditReasonInfo(conn, bookingId, "REJECT_BOOKING", row);
                    } else if ("COMPLETED".equalsIgnoreCase(status)) {
                        attachPricingInfo(conn, bookingId, row);
                    }

                    list.add(row);
                }
            }
        }
        return list;
    }

    private void attachCancellationInfo(Connection conn, int bookingId, Map<String, Object> row) throws Exception {
        String sql = "SELECT PenaltyPercent, PenaltyAmount, PenaltyStatus, Reason, CancelledAt "
                + "FROM Cancellation WHERE BookingID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    row.put("penaltyPercent", rs.getInt("PenaltyPercent"));
                    row.put("penaltyAmount", rs.getBigDecimal("PenaltyAmount"));
                    row.put("penaltyStatus", rs.getString("PenaltyStatus"));
                    row.put("cancelReason", rs.getString("Reason"));
                    Timestamp cancelledAt = rs.getTimestamp("CancelledAt");
                    row.put("cancelledAt", cancelledAt != null ? cancelledAt.toString() : null);
                }
            }
        }
    }

    private void attachAuditReasonInfo(Connection conn, int bookingId, String action, Map<String, Object> row) throws Exception {
        String sql = "SELECT TOP 1 NewValue, CreatedAt FROM AuditLog "
                + "WHERE EntityName = 'Booking' AND EntityID = ? AND Action = ? "
                + "ORDER BY CreatedAt DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(bookingId));
            ps.setString(2, action);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    row.put("rejectDetail", rs.getString("NewValue"));
                    row.put("rejectedAt", rs.getTimestamp("CreatedAt").toString());
                }
            }
        }
    }

    private void attachPricingInfo(Connection conn, int bookingId, Map<String, Object> row) throws Exception {
        String sql = "SELECT BaseFare, WeekendSurcharge, DiscountAmount, EstimatedTotal "
                + "FROM BookingPricing WHERE BookingID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    row.put("baseFare", rs.getBigDecimal("BaseFare"));
                    row.put("weekendSurcharge", rs.getBigDecimal("WeekendSurcharge"));
                    row.put("discountAmount", rs.getBigDecimal("DiscountAmount"));
                    row.put("totalAmount", rs.getBigDecimal("EstimatedTotal"));
                }
            }
        }
    }
}