package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

public class ComplaintDAO {

    public static class ComplaintForm {
        public String type;
        public String region;
        public String fullName;
        public String email;
        public String phone;
        public String province;
        public String issueType;
        public String fromLocation;
        public String toLocation;
        public BigDecimal fare;
        public Timestamp boardingTime;
        public String content;
        public Integer bookingId;
        public Integer customerId;
    }

    public int createComplaint(ComplaintForm f) throws Exception {
        String sql = "INSERT INTO Complaint "
                + "(ComplaintType, Region, FullName, Email, Phone, Province, IssueType, "
                + "FromLocation, ToLocation, Fare, BoardingTime, Content, BookingID, CustomerID, "
                + "Status, IsDeleted, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, GETDATE())";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.type);
            ps.setString(2, f.region);
            ps.setString(3, f.fullName);
            ps.setString(4, f.email);
            ps.setString(5, f.phone);
            ps.setString(6, f.province);
            ps.setString(7, f.issueType);
            ps.setString(8, f.fromLocation);
            ps.setString(9, f.toLocation);
            if (f.fare != null) {
                ps.setBigDecimal(10, f.fare);
            } else {
                ps.setNull(10, Types.DECIMAL);
            }
            if (f.boardingTime != null) {
                ps.setTimestamp(11, f.boardingTime);
            } else {
                ps.setNull(11, Types.TIMESTAMP);
            }
            ps.setString(12, f.content);
            if (f.bookingId != null) {
                ps.setInt(13, f.bookingId);
            } else {
                ps.setNull(13, Types.INTEGER);
            }
            if (f.customerId != null) {
                ps.setInt(14, f.customerId);
            } else {
                ps.setNull(14, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public List<Map<String, Object>> getComplaints() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM Complaint WHERE IsDeleted = 0 ORDER BY CreatedAt DESC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Map<String, Object>> getComplaintsByCustomerId(int customerId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM Complaint WHERE CustomerID = ? AND IsDeleted = 0 ORDER BY CreatedAt DESC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private Map<String, Object> mapRow(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("complaintId", rs.getInt("ComplaintID"));
        m.put("type", rs.getString("ComplaintType"));
        m.put("region", rs.getString("Region"));
        m.put("fullName", rs.getString("FullName"));
        m.put("email", rs.getString("Email"));
        m.put("phone", rs.getString("Phone"));
        m.put("province", rs.getString("Province"));
        m.put("issueType", rs.getString("IssueType"));
        m.put("fromLocation", rs.getString("FromLocation"));
        m.put("toLocation", rs.getString("ToLocation"));
        m.put("fare", rs.getBigDecimal("Fare"));
        Timestamp boardingTime = rs.getTimestamp("BoardingTime");
        m.put("boardingTime", boardingTime != null ? boardingTime.toString() : null);
        m.put("content", rs.getString("Content"));
        int bookingId = rs.getInt("BookingID");
        m.put("bookingId", rs.wasNull() ? null : bookingId);
        int customerId = rs.getInt("CustomerID");
        m.put("customerId", rs.wasNull() ? null : customerId);
        m.put("status", rs.getString("Status"));
        m.put("resolution", rs.getString("Resolution"));
        Timestamp resolvedAt = rs.getTimestamp("ResolvedAt");
        m.put("resolvedAt", resolvedAt != null ? resolvedAt.toString() : null);
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        m.put("createdAt", createdAt != null ? createdAt.toString() : null);
        return m;
    }

    public boolean resolveComplaint(int complaintId, String resolution) throws Exception {
        String sql = "UPDATE Complaint SET Status = 'RESOLVED', Resolution = ?, ResolvedAt = GETDATE() "
                + "WHERE ComplaintID = ? AND IsDeleted = 0";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolution);
            ps.setInt(2, complaintId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean softDeleteComplaint(int complaintId) throws Exception {
        String sql = "UPDATE Complaint SET IsDeleted = 1 WHERE ComplaintID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            return ps.executeUpdate() > 0;
        }
    }

    public int getCustomerAccountByComplaintId(int complaintId) throws Exception {
        String sql = "SELECT a.AccountID FROM Complaint cp "
                + "JOIN Customer c ON c.CustomerID = cp.CustomerID "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE cp.ComplaintID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return -1;
            }
        }
    }
}