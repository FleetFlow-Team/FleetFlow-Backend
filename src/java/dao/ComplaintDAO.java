package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

public class ComplaintDAO {

    public boolean createComplaint(int bookingId, int customerId, String content) throws Exception {
        String sql = "INSERT INTO Complaint (BookingID, CustomerID, Content, Status, CreatedAt) VALUES (?, ?, ?, 'PENDING', GETDATE())";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, customerId);
            ps.setString(3, content);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Map<String, Object>> getComplaints() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM Complaint ORDER BY CreatedAt DESC";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("complaintId", rs.getInt("ComplaintID"));
                map.put("bookingId", rs.getInt("BookingID"));
                map.put("customerId", rs.getInt("CustomerID"));
                map.put("content", rs.getString("Content"));
                map.put("status", rs.getString("Status"));
                
                // Add missing fields for frontend
                if (rs.getTimestamp("CreatedAt") != null) {
                    map.put("createdAt", rs.getTimestamp("CreatedAt").toString());
                }
                map.put("resolution", rs.getString("Resolution"));
                if (rs.getTimestamp("ResolvedAt") != null) {
                    map.put("resolvedAt", rs.getTimestamp("ResolvedAt").toString());
                }
                
                list.add(map);
            }
        }
        return list;
    }

    public boolean resolveComplaint(int complaintId, String resolution) throws Exception {
        String sql = "UPDATE Complaint SET Status = 'RESOLVED', Resolution = ?, ResolvedAt = GETDATE() WHERE ComplaintID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolution);
            ps.setInt(2, complaintId);
            return ps.executeUpdate() > 0;
        }
    }
}