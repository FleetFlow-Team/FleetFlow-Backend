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
                + "(BookingID, CustomerID, Content, Status, CreatedAt) "
                + "VALUES (?, ?, ?, 'PENDING', GETDATE())";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            // Đóng gói các thông tin (Type, Name, Phone...) vào Content vì CSDL không có cột riêng
            String formattedContent = String.format("[%s] %s\nHọ tên: %s\nLiên hệ: %s\nNội dung: %s",
                f.type != null ? f.type : "OTHER", 
                f.issueType != null ? "- " + f.issueType : "",
                f.fullName != null ? f.fullName : "Khách hàng", 
                f.phone != null ? f.phone : (f.email != null ? f.email : "N/A"), 
                f.content != null ? f.content : "");

            if (f.bookingId != null) {
                ps.setInt(1, f.bookingId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            
            if (f.customerId != null) {
                ps.setInt(2, f.customerId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            
            ps.setString(3, formattedContent.trim());

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
        
        String type = rs.getString("ComplaintType");
        String region = rs.getString("Region");
        String fullName = rs.getString("FullName");
        String email = rs.getString("Email");
        String phone = rs.getString("Phone");
        String province = rs.getString("Province");
        String issueType = rs.getString("IssueType");
        String content = rs.getString("Content");
        String resolution = rs.getString("Resolution");
        int bookingId = rs.getInt("BookingID");
        Integer bId = rs.wasNull() ? null : bookingId;
        int customerId = rs.getInt("CustomerID");
        Integer cId = rs.wasNull() ? null : customerId;

        // --- BÓC TÁCH & CHUẨN HÓA DỮ LIỆU THÔ TRONG CONTENT ---
        if (content != null && !content.trim().isEmpty()) {
            String str = content.trim();
            // 1. Tách Giải quyết:
            if (str.contains("Giải quyết:")) {
                int idx = str.indexOf("Giải quyết:");
                String resStr = str.substring(idx + 11).trim();
                if (resolution == null || resolution.trim().isEmpty()) {
                    resolution = resStr;
                }
                str = str.substring(0, idx).trim();
            }
            // 2. Tách Nội dung:
            String actualContent = str;
            if (str.contains("Nội dung:")) {
                int idx = str.indexOf("Nội dung:");
                actualContent = str.substring(idx + 9).trim();
                str = str.substring(0, idx).trim();
            }
            // 3. Tách Liên hệ:
            if (str.contains("Liên hệ:")) {
                int idx = str.indexOf("Liên hệ:");
                String pStr = str.substring(idx + 8).trim();
                if (phone == null || phone.trim().isEmpty() || "N/A".equalsIgnoreCase(phone) || isNumericOnly(phone) && phone.length() < 8) {
                    phone = pStr;
                }
                str = str.substring(0, idx).trim();
            }
            // 4. Tách Họ tên:
            if (str.contains("Họ tên:")) {
                int idx = str.indexOf("Họ tên:");
                String nStr = str.substring(idx + 7).trim();
                if (fullName == null || fullName.trim().isEmpty() || isNumericOnly(fullName)) {
                    fullName = nStr;
                }
                str = str.substring(0, idx).trim();
            }
            // 5. Phần còn lại chính là Loại và Vấn đề
            if (str.startsWith("[")) {
                int closeIdx = str.indexOf("]");
                if (closeIdx != -1) {
                    String iss = str.substring(closeIdx + 1).replaceAll("^[-\\s]+", "").trim();
                    if ((issueType == null || issueType.trim().isEmpty() || issueType.contains("Họ tên:") || issueType.contains("Nội dung:")) && !iss.isEmpty()) {
                        issueType = iss;
                    }
                    if (type == null || type.trim().isEmpty() || "OTHER".equalsIgnoreCase(type)) {
                        type = str.substring(1, closeIdx).trim();
                    }
                    if (actualContent.equals(content.trim()) || actualContent.isEmpty()) {
                        if (iss.isEmpty()) actualContent = str.substring(closeIdx + 1).trim();
                    }
                } else {
                    if (issueType == null || issueType.trim().isEmpty()) {
                        issueType = str.replaceAll("^[-\\s]+", "").trim();
                    }
                }
            } else if (!str.equals(actualContent) && !str.isEmpty()) {
                if (issueType == null || issueType.trim().isEmpty()) {
                    issueType = str;
                }
            }
            content = (actualContent == null || actualContent.isEmpty()) ? "Không có chi tiết" : actualContent;
        }

        // --- LÀM SẠCH CÁC TRƯỜNG LƯU NHẦM SỐ VÔ NGHĨA ---
        if (fullName == null || fullName.trim().isEmpty() || isNumericOnly(fullName)) {
            fullName = (cId != null) ? "Thành viên #" + cId : "Khách vãng lai";
        }
        if (phone == null || phone.trim().isEmpty()) {
            phone = "N/A";
        }
        if (province != null && !province.trim().isEmpty() && isNumericOnly(province)) {
            if (bId != null && String.valueOf(bId).equals(province.trim())) {
                province = "Chuyến #" + bId;
            } else {
                province = "Khu vực #" + province;
            }
        }
        if (region != null && !region.trim().isEmpty() && isNumericOnly(region)) {
            region = "Khu vực #" + region;
        }

        m.put("type", type != null ? type : "OTHER");
        m.put("region", region);
        m.put("fullName", fullName);
        m.put("email", email);
        m.put("phone", phone);
        m.put("province", province);
        m.put("issueType", issueType != null ? issueType : "");
        m.put("fromLocation", rs.getString("FromLocation"));
        m.put("toLocation", rs.getString("ToLocation"));
        m.put("fare", rs.getBigDecimal("Fare"));
        Timestamp boardingTime = rs.getTimestamp("BoardingTime");
        m.put("boardingTime", boardingTime != null ? boardingTime.toString() : null);
        m.put("content", content);
        m.put("bookingId", bId);
        m.put("customerId", cId);
        m.put("status", rs.getString("Status"));
        m.put("resolution", resolution);
        Timestamp resolvedAt = rs.getTimestamp("ResolvedAt");
        m.put("resolvedAt", resolvedAt != null ? resolvedAt.toString() : null);
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        m.put("createdAt", createdAt != null ? createdAt.toString() : null);
        return m;
    }

    private boolean isNumericOnly(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        try {
            Long.parseLong(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
}