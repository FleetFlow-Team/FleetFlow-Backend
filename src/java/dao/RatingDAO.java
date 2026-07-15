/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

/**
 *
 * @author asus
 */
public class RatingDAO {

    public boolean isCustomerRatingLocked(int bookingId) throws Exception {
        String sql = "SELECT DATEDIFF(DAY, UpdatedAt, GETDATE()) AS DaysPassed FROM Booking WHERE BookingID = ? AND Status = 'COMPLETED'";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("DaysPassed") > 7;
                }
            }
        }
        return true;
    }

    public int countCustomerRating(int bookingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM CustomerRating WHERE BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countDriverRating(int bookingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM DriverRating WHERE BookingID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public boolean submitCustomerRating(int bookingId, int driverRating, int carRating, String comment) throws Exception {
        String sql = "INSERT INTO CustomerRating (BookingID, DriverRating, CarRating, Comment, CreatedAt) VALUES (?, ?, ?, ?, GETDATE())";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, driverRating);
            ps.setInt(3, carRating);
            ps.setString(4, comment);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean submitDriverRating(int bookingId, int customerRating, String comment) throws Exception {
        String sql = "INSERT INTO DriverRating (BookingID, CustomerRating, Comment, CreatedAt) VALUES (?, ?, ?, GETDATE())";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, customerRating);
            ps.setString(3, comment);
            return ps.executeUpdate() > 0;
        }
    }

    public List<String> getInactiveCustomerEmails(int days) throws Exception {
        List<String> emails = new ArrayList<>();
        String sql = "SELECT a.Email FROM Customer c JOIN Account a ON c.AccountID = a.AccountID WHERE c.CustomerID NOT IN (SELECT DISTINCT CustomerID FROM Booking WHERE CreatedAt >= DATEADD(DAY, -?, GETDATE()))";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    emails.add(rs.getString("Email"));
                }
            }
        }
        return emails;
    }

    /**
     * Khách hàng không có booking nào trong {@code days} ngày gần nhất, VÀ chưa nhận
     * email của đúng campaign này trong {@code days} ngày gần nhất (chống spam khi
     * scheduler chạy lặp lại nhiều lần mà khách vẫn còn inactive).
     */
    public List<Map<String, Object>> getInactiveCustomers(int days, int campaignId) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT a.AccountID, a.Email, a.FullName "
                + "FROM Customer c JOIN Account a ON c.AccountID = a.AccountID "
                + "WHERE c.CustomerID NOT IN (SELECT DISTINCT CustomerID FROM Booking WHERE CreatedAt >= DATEADD(DAY, -?, GETDATE())) "
                + "AND NOT EXISTS (SELECT 1 FROM EmailLog el WHERE el.CampaignID = ? AND el.RecipientAccountID = a.AccountID AND el.Status = 'Success' AND el.SentAt >= DATEADD(DAY, -?, GETDATE()))";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days);
            ps.setInt(2, campaignId);
            ps.setInt(3, days);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("AccountID", rs.getInt("AccountID"));
                    row.put("Email", rs.getString("Email"));
                    row.put("FullName", rs.getString("FullName"));
                    result.add(row);
                }
            }
        }
        return result;
    }

    public List<Map<String, Object>> getRatingsByCustomerId(int customerId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT cr.RatingID, cr.BookingID, cr.DriverRating, cr.CarRating, cr.Comment, cr.CreatedAt, "
                + "b.BookingType, v.Brand, v.Model, v.LicensePlate, da.FullName AS DriverName "
                + "FROM CustomerRating cr "
                + "JOIN Booking b ON b.BookingID = cr.BookingID "
                + "LEFT JOIN Vehicle v ON v.VehicleID = b.VehicleID "
                + "LEFT JOIN DriverJobBroadcast djb ON djb.BookingID = b.BookingID AND djb.Status = 'ACCEPTED' "
                + "LEFT JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "LEFT JOIN Account da ON da.AccountID = d.AccountID "
                + "WHERE b.CustomerID = ? "
                + "ORDER BY cr.CreatedAt DESC";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ratingId", rs.getInt("RatingID"));
                    m.put("bookingId", rs.getInt("BookingID"));
                    m.put("driverRating", rs.getInt("DriverRating"));
                    m.put("carRating", rs.getInt("CarRating"));
                    m.put("comment", rs.getString("Comment"));
                    String brand = rs.getString("Brand");
                    String model = rs.getString("Model");
                    m.put("vehicleName", (brand == null ? "" : brand) + " " + (model == null ? "" : model));
                    m.put("licensePlate", rs.getString("LicensePlate"));
                    m.put("bookingType", rs.getString("BookingType"));
                    m.put("driverName", rs.getString("DriverName"));
                    java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    m.put("createdAt", createdAt != null ? createdAt.toString() : null);
                    list.add(m);
                }
            }
        }
        return list;
    }

    private static final int LOW_RATING_THRESHOLD = 2;

    /**
     * Điểm trung bình + số rating thấp theo từng tài xế, xếp tệ nhất lên đầu — dùng cho
     * Admin theo dõi chất lượng tài xế qua CustomerRating (khách đánh giá tài xế/xe).
     */
    public List<Map<String, Object>> getDriverQualityStats(String fromDate, String toDate) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT d.DriverID, da.FullName AS DriverName, "
                + "AVG(CAST(cr.DriverRating AS DECIMAL(5,2))) AS AvgDriverRating, "
                + "AVG(CAST(cr.CarRating AS DECIMAL(5,2))) AS AvgCarRating, "
                + "COUNT(*) AS RatingCount, "
                + "SUM(CASE WHEN cr.DriverRating <= " + LOW_RATING_THRESHOLD + " OR cr.CarRating <= " + LOW_RATING_THRESHOLD + " THEN 1 ELSE 0 END) AS LowRatingCount "
                + "FROM CustomerRating cr "
                + "JOIN Booking b ON b.BookingID = cr.BookingID "
                + "JOIN DriverJobBroadcast djb ON djb.BookingID = b.BookingID AND djb.Status = 'ACCEPTED' "
                + "JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "JOIN Account da ON da.AccountID = d.AccountID "
                + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND cr.CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND cr.CreatedAt <= ? ");
            params.add(toDate);
        }
        sql.append("GROUP BY d.DriverID, da.FullName ORDER BY AvgDriverRating ASC");

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("driverId", rs.getInt("DriverID"));
                    m.put("driverName", rs.getString("DriverName"));
                    m.put("avgDriverRating", rs.getBigDecimal("AvgDriverRating"));
                    m.put("avgCarRating", rs.getBigDecimal("AvgCarRating"));
                    m.put("ratingCount", rs.getInt("RatingCount"));
                    m.put("lowRatingCount", rs.getInt("LowRatingCount"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    /** Danh sách CustomerRating (khách đánh giá tài xế/xe) cho Admin, có filter tùy chọn. */
    public List<Map<String, Object>> getCustomerRatingsForAdmin(Integer driverId, Integer customerId, Integer bookingId,
            boolean lowOnly, String fromDate, String toDate) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT cr.RatingID, cr.BookingID, cr.DriverRating, cr.CarRating, cr.Comment, cr.CreatedAt, "
                + "b.CustomerID, ca.FullName AS CustomerName, d.DriverID, da.FullName AS DriverName, "
                + "v.Brand, v.Model, v.LicensePlate "
                + "FROM CustomerRating cr "
                + "JOIN Booking b ON b.BookingID = cr.BookingID "
                + "JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "JOIN Account ca ON ca.AccountID = c.AccountID "
                + "LEFT JOIN Vehicle v ON v.VehicleID = b.VehicleID "
                + "LEFT JOIN DriverJobBroadcast djb ON djb.BookingID = b.BookingID AND djb.Status = 'ACCEPTED' "
                + "LEFT JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "LEFT JOIN Account da ON da.AccountID = d.AccountID "
                + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (bookingId != null) {
            sql.append("AND cr.BookingID = ? ");
            params.add(bookingId);
        }
        if (customerId != null) {
            sql.append("AND b.CustomerID = ? ");
            params.add(customerId);
        }
        if (driverId != null) {
            sql.append("AND d.DriverID = ? ");
            params.add(driverId);
        }
        if (lowOnly) {
            sql.append("AND (cr.DriverRating <= " + LOW_RATING_THRESHOLD + " OR cr.CarRating <= " + LOW_RATING_THRESHOLD + ") ");
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND cr.CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND cr.CreatedAt <= ? ");
            params.add(toDate);
        }
        sql.append("ORDER BY cr.CreatedAt DESC");

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ratingId", rs.getInt("RatingID"));
                    m.put("bookingId", rs.getInt("BookingID"));
                    m.put("driverRating", rs.getInt("DriverRating"));
                    m.put("carRating", rs.getInt("CarRating"));
                    m.put("comment", rs.getString("Comment"));
                    m.put("customerId", rs.getInt("CustomerID"));
                    m.put("customerName", rs.getString("CustomerName"));
                    int driverIdVal = rs.getInt("DriverID");
                    m.put("driverId", rs.wasNull() ? null : driverIdVal);
                    m.put("driverName", rs.getString("DriverName"));
                    String brand = rs.getString("Brand");
                    String model = rs.getString("Model");
                    m.put("vehicleName", (brand == null ? "" : brand) + " " + (model == null ? "" : model));
                    m.put("licensePlate", rs.getString("LicensePlate"));
                    java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    m.put("createdAt", createdAt != null ? createdAt.toString() : null);
                    list.add(m);
                }
            }
        }
        return list;
    }

    /** Tổng quan CustomerRating khớp filter (không áp dụng lowOnly) — dùng cho phần summary. */
    public Map<String, Object> getCustomerRatingSummaryForAdmin(Integer driverId, Integer customerId, Integer bookingId,
            String fromDate, String toDate) throws Exception {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS Total, "
                + "AVG(CAST(cr.DriverRating AS DECIMAL(5,2))) AS AvgDriverRating, "
                + "AVG(CAST(cr.CarRating AS DECIMAL(5,2))) AS AvgCarRating, "
                + "SUM(CASE WHEN cr.DriverRating <= " + LOW_RATING_THRESHOLD + " OR cr.CarRating <= " + LOW_RATING_THRESHOLD + " THEN 1 ELSE 0 END) AS LowRatingCount "
                + "FROM CustomerRating cr "
                + "JOIN Booking b ON b.BookingID = cr.BookingID "
                + "LEFT JOIN DriverJobBroadcast djb ON djb.BookingID = b.BookingID AND djb.Status = 'ACCEPTED' "
                + "LEFT JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (bookingId != null) {
            sql.append("AND cr.BookingID = ? ");
            params.add(bookingId);
        }
        if (customerId != null) {
            sql.append("AND b.CustomerID = ? ");
            params.add(customerId);
        }
        if (driverId != null) {
            sql.append("AND d.DriverID = ? ");
            params.add(driverId);
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND cr.CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND cr.CreatedAt <= ? ");
            params.add(toDate);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    m.put("totalRatings", rs.getInt("Total"));
                    m.put("averageDriverRating", rs.getBigDecimal("AvgDriverRating"));
                    m.put("averageCarRating", rs.getBigDecimal("AvgCarRating"));
                    m.put("lowRatingCount", rs.getInt("LowRatingCount"));
                }
            }
        }
        return m;
    }

    /** Danh sách DriverRating (tài xế đánh giá khách) cho Admin, có filter tùy chọn. */
    public List<Map<String, Object>> getDriverRatingsForAdmin(Integer driverId, Integer customerId, Integer bookingId,
            boolean lowOnly, String fromDate, String toDate) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT dr.RatingID, dr.BookingID, dr.CustomerRating, dr.Comment, dr.CreatedAt, "
                + "b.CustomerID, ca.FullName AS CustomerName, d.DriverID, da.FullName AS DriverName "
                + "FROM DriverRating dr "
                + "JOIN Booking b ON b.BookingID = dr.BookingID "
                + "JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "JOIN Account ca ON ca.AccountID = c.AccountID "
                + "LEFT JOIN DriverJobBroadcast djb ON djb.BookingID = b.BookingID AND djb.Status = 'ACCEPTED' "
                + "LEFT JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "LEFT JOIN Account da ON da.AccountID = d.AccountID "
                + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (bookingId != null) {
            sql.append("AND dr.BookingID = ? ");
            params.add(bookingId);
        }
        if (customerId != null) {
            sql.append("AND b.CustomerID = ? ");
            params.add(customerId);
        }
        if (driverId != null) {
            sql.append("AND d.DriverID = ? ");
            params.add(driverId);
        }
        if (lowOnly) {
            sql.append("AND dr.CustomerRating <= " + LOW_RATING_THRESHOLD + " ");
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND dr.CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND dr.CreatedAt <= ? ");
            params.add(toDate);
        }
        sql.append("ORDER BY dr.CreatedAt DESC");

        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ratingId", rs.getInt("RatingID"));
                    m.put("bookingId", rs.getInt("BookingID"));
                    m.put("customerRating", rs.getInt("CustomerRating"));
                    m.put("comment", rs.getString("Comment"));
                    m.put("customerId", rs.getInt("CustomerID"));
                    m.put("customerName", rs.getString("CustomerName"));
                    int driverIdVal = rs.getInt("DriverID");
                    m.put("driverId", rs.wasNull() ? null : driverIdVal);
                    m.put("driverName", rs.getString("DriverName"));
                    java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    m.put("createdAt", createdAt != null ? createdAt.toString() : null);
                    list.add(m);
                }
            }
        }
        return list;
    }

    /** Tổng quan DriverRating khớp filter (không áp dụng lowOnly) — dùng cho phần summary. */
    public Map<String, Object> getDriverRatingSummaryForAdmin(Integer driverId, Integer customerId, Integer bookingId,
            String fromDate, String toDate) throws Exception {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS Total, "
                + "AVG(CAST(dr.CustomerRating AS DECIMAL(5,2))) AS AvgCustomerRating, "
                + "SUM(CASE WHEN dr.CustomerRating <= " + LOW_RATING_THRESHOLD + " THEN 1 ELSE 0 END) AS LowRatingCount "
                + "FROM DriverRating dr "
                + "JOIN Booking b ON b.BookingID = dr.BookingID "
                + "LEFT JOIN DriverJobBroadcast djb ON djb.BookingID = b.BookingID AND djb.Status = 'ACCEPTED' "
                + "LEFT JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (bookingId != null) {
            sql.append("AND dr.BookingID = ? ");
            params.add(bookingId);
        }
        if (customerId != null) {
            sql.append("AND b.CustomerID = ? ");
            params.add(customerId);
        }
        if (driverId != null) {
            sql.append("AND d.DriverID = ? ");
            params.add(driverId);
        }
        if (fromDate != null && !fromDate.isEmpty()) {
            sql.append("AND dr.CreatedAt >= ? ");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isEmpty()) {
            sql.append("AND dr.CreatedAt <= ? ");
            params.add(toDate);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    m.put("totalRatings", rs.getInt("Total"));
                    m.put("averageCustomerRating", rs.getBigDecimal("AvgCustomerRating"));
                    m.put("lowRatingCount", rs.getInt("LowRatingCount"));
                }
            }
        }
        return m;
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer) {
                ps.setInt(i + 1, (Integer) p);
            } else {
                ps.setString(i + 1, (String) p);
            }
        }
    }
}