package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import model.Landmark;
import utils.DbUtils;

/**
 * DAO cho danh mục "điểm đến cố định" (Landmark) — Bến xe Miền Tây, Miền
 * Đông, sân bay Tân Sơn Nhất, Nội Bài, Vũng Tàu... Đây chỉ là danh sách gợi
 * ý điểm đến cho khách chọn nhanh; khi đặt xe vẫn dùng chung bookingType =
 * DISTANCE như mọi điểm-tới-điểm tự do khác (không phân loại nội/liên tỉnh
 * — xem giải thích trong Landmark.java).
 */
public class LandmarkDAO {

    private Landmark mapRow(ResultSet rs) throws Exception {
        Landmark l = new Landmark();
        l.setId(rs.getInt("LandmarkID"));
        l.setName(rs.getString("Name"));
        l.setAddress(rs.getString("Address"));
        l.setLat(rs.getBigDecimal("Lat"));
        l.setLng(rs.getBigDecimal("Lng"));
        l.setCategory(rs.getString("Category"));
        l.setCreatedBy(rs.getInt("CreatedBy"));
        l.setCreatedAt(rs.getTimestamp("CreatedAt"));
        l.setDeleted(rs.getBoolean("IsDeleted"));
        return l;
    }

    /**
     * Danh sách landmark đang hoạt động, cho khách chọn khi đặt xe.
     */
    public List<Landmark> getActiveLandmarks() throws Exception {
        List<Landmark> list = new ArrayList<>();
        String sql = "SELECT * FROM Landmark WHERE IsDeleted = 0 ORDER BY Category, Name";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Toàn bộ landmark kể cả đã ẩn — dùng cho màn quản lý của Admin.
     */
    public List<Landmark> getAllLandmarks() throws Exception {
        List<Landmark> list = new ArrayList<>();
        String sql = "SELECT * FROM Landmark ORDER BY IsDeleted, Category, Name";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Landmark getLandmarkById(int landmarkId) throws Exception {
        String sql = "SELECT * FROM Landmark WHERE LandmarkID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, landmarkId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public int createLandmark(String name, String address, BigDecimal lat, BigDecimal lng,
            String category, int createdBy) throws Exception {
        String sql = "INSERT INTO Landmark "
                + "(Name, Address, Lat, Lng, Category, CreatedBy, CreatedAt, IsDeleted) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = DbUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, address);
            ps.setBigDecimal(3, lat);
            ps.setBigDecimal(4, lng);
            ps.setString(5, category);
            ps.setInt(6, createdBy);
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Update linh hoạt — field nào null thì giữ nguyên giá trị cũ (COALESCE).
     */
    public boolean updateLandmark(int landmarkId, String name, String address,
            BigDecimal lat, BigDecimal lng, String category) throws Exception {
        String sql = "UPDATE Landmark SET "
                + "Name = COALESCE(?, Name), Address = COALESCE(?, Address), "
                + "Lat = COALESCE(?, Lat), Lng = COALESCE(?, Lng), "
                + "Category = COALESCE(?, Category) "
                + "WHERE LandmarkID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, address);
            if (lat != null) {
                ps.setBigDecimal(3, lat);
            } else {
                ps.setNull(3, Types.DECIMAL);
            }
            if (lng != null) {
                ps.setBigDecimal(4, lng);
            } else {
                ps.setNull(4, Types.DECIMAL);
            }
            ps.setString(5, category);
            ps.setInt(6, landmarkId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Ẩn landmark (soft delete) — không xóa cứng vì các booking cũ đã tham
     * chiếu tới địa chỉ/tọa độ này trong BookingDetail (lưu tách bản ghi độc
     * lập, không FK ràng buộc).
     */
    public boolean deleteLandmark(int landmarkId) throws Exception {
        String sql = "UPDATE Landmark SET IsDeleted = 1 WHERE LandmarkID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, landmarkId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean restoreLandmark(int landmarkId) throws Exception {
        String sql = "UPDATE Landmark SET IsDeleted = 0 WHERE LandmarkID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, landmarkId);
            return ps.executeUpdate() > 0;
        }
    }
}