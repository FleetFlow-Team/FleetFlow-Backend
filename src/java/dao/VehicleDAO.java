package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.VehicleAIData;
import utils.DbUtils;

/**
 *
 * @author User
 */
public class VehicleDAO {
    private static final String BASE_SELECT =
            "SELECT v.VehicleID, v.VehicleTypeID, vt.TypeName, "
          + "v.LicensePlate, v.ChassisNumber, v.EngineNumber, v.Brand, v.Model, "
          + "v.SeatCount, v.Status, v.AccumulatedKm, v.Description, "
          + "STRING_AGG(t.TagName, ', ') AS Tags "
          + "FROM Vehicle v "
          + "JOIN VehicleType vt ON vt.VehicleTypeID = v.VehicleTypeID "
          + "LEFT JOIN VehicleTag vtg ON vtg.VehicleID = v.VehicleID "
          + "LEFT JOIN Tag t ON t.TagID = vtg.TagID ";

    private static final String GROUP_BY =
            " GROUP BY v.VehicleID, v.VehicleTypeID, vt.TypeName, "
          + "v.LicensePlate, v.ChassisNumber, v.EngineNumber, v.Brand, v.Model, "
          + "v.SeatCount, v.Status, v.AccumulatedKm, v.Description ";

    public List<Map<String, Object>> findAvailable(Integer seatCount, Integer typeId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(BASE_SELECT)
                .append("WHERE UPPER(v.Status) = 'AVAILABLE' ");
        List<Integer> params = new ArrayList<>();
        if (seatCount != null) {
            sql.append("AND v.SeatCount = ? ");
            params.add(seatCount);
        }
        if (typeId != null) {
            sql.append("AND v.VehicleTypeID = ? ");
            params.add(typeId);
        }
        sql.append(GROUP_BY).append("ORDER BY v.SeatCount, v.Brand");

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setInt(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapVehicle(rs));
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> findAvailable(Integer seatCount, Integer typeId, String bookingType) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(BASE_SELECT)
                .append("WHERE UPPER(v.Status) = 'AVAILABLE' ")
                .append("AND EXISTS (SELECT 1 FROM PricingRule pr WHERE pr.VehicleTypeID = v.VehicleTypeID AND UPPER(pr.BookingType) = UPPER(?)) ");
        
        List<Object> params = new ArrayList<>();
        params.add(bookingType);
        
        if (seatCount != null) {
            sql.append("AND v.SeatCount = ? ");
            params.add(seatCount);
        }
        if (typeId != null) {
            sql.append("AND v.VehicleTypeID = ? ");
            params.add(typeId);
        }
        sql.append(GROUP_BY).append("ORDER BY v.SeatCount, v.Brand");

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    ps.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    ps.setInt(i + 1, (Integer) param);
                }
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapVehicle(rs));
                }
            }
        }
        return list;
    }
    
    public List<Map<String, Object>> findAll() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = BASE_SELECT + GROUP_BY + "ORDER BY v.VehicleID";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapVehicle(rs));
            }
        }
        return list;
    }

    public Map<String, Object> findById(int vehicleId) throws Exception {
        String sql = BASE_SELECT + "WHERE v.VehicleID = ? " + GROUP_BY;
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapVehicle(rs);
                }
                return null;
            }
        }
    }
    
    public List<Map<String, Object>> findVehiclesWithFilters(
            Integer seatCount, 
            String brand, 
            String fuelType,
            String bookingType, 
            BigDecimal priceMin, 
            BigDecimal priceMax) {
        
        List<Map<String, Object>> list = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT v.VehicleID, v.VehicleTypeID, vt.TypeName, " +
            "v.LicensePlate, v.ChassisNumber, v.EngineNumber, v.Brand, v.Model, " +
            "v.SeatCount, v.FuelType, v.Status, v.AccumulatedKm, v.Description, v.ImagePath " +
            "FROM Vehicle v " +
            "LEFT JOIN VehicleType vt ON vt.VehicleTypeID = v.VehicleTypeID " +
            "LEFT JOIN PricingRule pr ON pr.VehicleTypeID = v.VehicleTypeID " +
            "WHERE UPPER(v.Status) = 'AVAILABLE'"
        );
        
        List<Object> parameters = new ArrayList<>();
        
        if (seatCount != null) {
            sql.append(" AND v.SeatCount = ? ");
            parameters.add(seatCount);
        }
        
        if (brand != null && !brand.trim().isEmpty()) {
            sql.append(" AND UPPER(v.Brand) = UPPER(?) ");
            parameters.add(brand.trim());
        }
        
        if (fuelType != null && !fuelType.trim().isEmpty()) {
            sql.append(" AND UPPER(v.FuelType) = UPPER(?) ");
            parameters.add(fuelType.trim());
        }
        
        if (bookingType != null && !bookingType.trim().isEmpty()) {
            sql.append(" AND UPPER(pr.BookingType) = UPPER(?) ");
            parameters.add(bookingType.trim());
        }
        
        if (priceMin != null || priceMax != null) {
            String priceColumn = "pr.BasePrice"; 
            
            if (bookingType != null) {
                switch (bookingType.toUpperCase()) {
                    case "HOURLY":
                        priceColumn = "pr.PricePerHour";
                        break;
                    case "DAILY":
                        priceColumn = "pr.PricePerDay";
                        break;
                    case "DISTANCE":
                        priceColumn = "pr.PricePerKm";
                        break;
                }
            }
            
            if (priceMin != null) {
                sql.append(" AND ").append(priceColumn).append(" >= ? ");
                parameters.add(priceMin);
            }
            if (priceMax != null) {
                sql.append(" AND ").append(priceColumn).append(" <= ? ");
                parameters.add(priceMax);
            }
        }
        sql.append(" ORDER BY v.VehicleID ASC");
        
        try (Connection con = DbUtils.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> vehicle = new HashMap<>();
                    vehicle.put("vehicleId", rs.getInt("VehicleID"));
                    vehicle.put("vehicleTypeId", rs.getInt("VehicleTypeID"));
                    vehicle.put("vehicleType", rs.getString("TypeName"));
                    vehicle.put("licensePlate", rs.getString("LicensePlate"));
                    vehicle.put("brand", rs.getString("Brand"));
                    vehicle.put("model", rs.getString("Model"));
                    vehicle.put("seatCount", rs.getInt("SeatCount"));
                    vehicle.put("fuelType", rs.getString("FuelType"));
                    vehicle.put("status", rs.getString("Status"));
                    vehicle.put("description", rs.getString("Description"));
                    vehicle.put("imagePath", rs.getString("ImagePath"));
                    
                    list.add(vehicle);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return list;
    }

    public int create(int vehicleTypeId, String licensePlate, String chassisNumber, String engineNumber,
            String brand, String model, int seatCount, String status, Integer accumulatedKm,
            int createdBy, String description) throws Exception {
        String sql = "INSERT INTO Vehicle "
                + "(VehicleTypeID, LicensePlate, ChassisNumber, EngineNumber, Brand, Model, "
                + "SeatCount, Status, AccumulatedKm, CreatedBy, CreatedAt, Description) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, vehicleTypeId);
            ps.setString(2, licensePlate);
            ps.setString(3, chassisNumber);
            ps.setString(4, engineNumber);
            ps.setString(5, brand);
            ps.setString(6, model);
            ps.setInt(7, seatCount);
            ps.setString(8, status);
            ps.setInt(9, accumulatedKm == null ? 0 : accumulatedKm);
            ps.setInt(10, createdBy);
            ps.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
            ps.setString(12, description);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    public boolean update(int vehicleId, Integer vehicleTypeId, String licensePlate, String chassisNumber,
            String engineNumber, String brand, String model, Integer seatCount, String status,
            Integer accumulatedKm, String description) throws Exception {
        String sql = "UPDATE Vehicle SET "
                + "VehicleTypeID = COALESCE(?, VehicleTypeID), "
                + "LicensePlate = COALESCE(?, LicensePlate), "
                + "ChassisNumber = COALESCE(?, ChassisNumber), "
                + "EngineNumber = COALESCE(?, EngineNumber), "
                + "Brand = COALESCE(?, Brand), "
                + "Model = COALESCE(?, Model), "
                + "SeatCount = COALESCE(?, SeatCount), "
                + "Status = COALESCE(?, Status), "
                + "AccumulatedKm = COALESCE(?, AccumulatedKm), "
                + "Description = COALESCE(?, Description) "
                + "WHERE VehicleID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setIntOrNull(ps, 1, vehicleTypeId);
            ps.setString(2, licensePlate);
            ps.setString(3, chassisNumber);
            ps.setString(4, engineNumber);
            ps.setString(5, brand);
            ps.setString(6, model);
            setIntOrNull(ps, 7, seatCount);
            ps.setString(8, status);
            setIntOrNull(ps, 9, accumulatedKm);
            ps.setString(10, description);
            ps.setInt(11, vehicleId);
            return ps.executeUpdate() > 0;
        }
    }
    
    public int getAccountIdByEmail(String email) throws Exception {
        String sql = "SELECT AccountID FROM Account WHERE Email = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return 0;
            }
        }
    }

    public int countBookingsByVehicle(int vehicleId) throws Exception {
        String sql = "SELECT COUNT(*) AS Cnt FROM Booking WHERE VehicleID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Cnt");
                }
                return 0;
            }
        }
    }

    public void deleteWithChildren(int vehicleId) throws Exception {
        Connection conn = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement("DELETE FROM VehicleTag WHERE VehicleID = ?")) {
                p1.setInt(1, vehicleId);
                p1.executeUpdate();
            }
            try (PreparedStatement p2 = conn.prepareStatement("DELETE FROM VehicleDocument WHERE VehicleID = ?")) {
                p2.setInt(1, vehicleId);
                p2.executeUpdate();
            }
            try (PreparedStatement p3 = conn.prepareStatement("DELETE FROM Vehicle WHERE VehicleID = ?")) {
                p3.setInt(1, vehicleId);
                p3.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    private void setIntOrNull(PreparedStatement ps, int idx, Integer val) throws Exception {
        if (val == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, val);
        }
    }

    private Map<String, Object> mapVehicle(ResultSet rs) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vehicleId", rs.getInt("VehicleID"));
        m.put("vehicleTypeId", rs.getInt("VehicleTypeID"));
        m.put("typeName", rs.getString("TypeName"));
        m.put("licensePlate", rs.getString("LicensePlate"));
        m.put("chassisNumber", rs.getString("ChassisNumber"));
        m.put("engineNumber", rs.getString("EngineNumber"));
        m.put("brand", rs.getString("Brand"));
        m.put("model", rs.getString("Model"));
        m.put("seatCount", rs.getInt("SeatCount"));
        m.put("status", rs.getString("Status"));
        int km = rs.getInt("AccumulatedKm");
        m.put("accumulatedKm", rs.wasNull() ? null : km);
        m.put("description", rs.getString("Description"));
        m.put("tags", rs.getString("Tags"));
        return m;
    }

    /**
     * BE-68: GET /api/v1/admin/vehicles/{id}/tags — Xem tags AI của 1 xe.
     */
    public List<Map<String, Object>> getTagsByVehicleId(int vehicleId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT t.TagID, t.TagName, t.Description "
                + "FROM VehicleTag vtg JOIN Tag t ON t.TagID = vtg.TagID "
                + "WHERE vtg.VehicleID = ? ORDER BY t.TagName";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tagId", rs.getInt("TagID"));
                    m.put("tagName", rs.getString("TagName"));
                    m.put("description", rs.getString("Description"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    /**
     * BE-69: PUT /api/v1/admin/vehicles/{id}/tags — Cập nhật tags AI cho xe
     * (ví dụ: "êm ái", "cốp rộng"...). Ghi đè toàn bộ danh sách tag hiện tại.
     * Tag chưa tồn tại trong bảng Tag sẽ được tự tạo mới kèm description.
     * Nếu tag đã tồn tại và description gửi lên khác null/rỗng → cập nhật
     * luôn description mới cho Tag đó (áp dụng chung, không riêng theo xe).
     *
     * @param tagItems List các tag, mỗi item: {"tagName": "...", "description": "..." (optional)}
     */
    public void updateVehicleTags(int vehicleId, List<Map<String, String>> tagItems) throws Exception {
        Connection conn = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM VehicleTag WHERE VehicleID = ?")) {
                del.setInt(1, vehicleId);
                del.executeUpdate();
            }

            if (tagItems != null) {
                for (Map<String, String> item : tagItems) {
                    String tagName = item.get("tagName");
                    if (tagName == null || tagName.trim().isEmpty()) {
                        continue;
                    }
                    tagName = tagName.trim();
                    String description = item.get("description");
                    if (description != null) {
                        description = description.trim();
                        if (description.isEmpty()) {
                            description = null;
                        }
                    }

                    int tagId = findOrCreateTag(conn, tagName, description);

                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO VehicleTag (VehicleID, TagID) VALUES (?, ?)")) {
                        ins.setInt(1, vehicleId);
                        ins.setInt(2, tagId);
                        ins.executeUpdate();
                    }
                }
            }

            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    private int findOrCreateTag(Connection conn, String tagName, String description) throws Exception {
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT TagID FROM Tag WHERE TagName = ?")) {
            find.setString(1, tagName);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    int existingTagId = rs.getInt("TagID");
                    // Tag đã tồn tại — nếu có description mới gửi lên thì cập nhật luôn
                    if (description != null) {
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE Tag SET Description = ? WHERE TagID = ?")) {
                            upd.setString(1, description);
                            upd.setInt(2, existingTagId);
                            upd.executeUpdate();
                        }
                    }
                    return existingTagId;
                }
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO Tag (TagName, Description) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, tagName);
            ins.setString(2, description);
            ins.executeUpdate();
            try (ResultSet rs = ins.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new Exception("Không thể tạo Tag mới: " + tagName);
    }

    public List<VehicleAIData> getVehiclesForAI() {

        List<VehicleAIData> list = new ArrayList<>();

        String sql =
                "SELECT " +
                "v.VehicleID, " +
                "v.Brand, " +
                "v.Model, " +
                "vt.TypeName, " +
                "v.SeatCount, " +
                "v.Description, " +
                "STRING_AGG(t.TagName, ', ') AS Tags " +
                "FROM Vehicle v " +
                "JOIN VehicleType vt ON vt.VehicleTypeID = v.VehicleTypeID " +
                "LEFT JOIN VehicleTag vtg ON vtg.VehicleID = v.VehicleID " +
                "LEFT JOIN Tag t ON t.TagID = vtg.TagID " +
                "GROUP BY " +
                "v.VehicleID, " +
                "v.Brand, " +
                "v.Model, " +
                "vt.TypeName, " +
                "v.SeatCount, " +
                "v.Description";

        try (
                Connection con = DbUtils.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                VehicleAIData vehicle = new VehicleAIData();

                vehicle.setVehicleId(rs.getInt("VehicleID"));
                vehicle.setBrand(rs.getString("Brand"));
                vehicle.setModel(rs.getString("Model"));
                vehicle.setVehicleType(rs.getString("TypeName"));
                vehicle.setSeatCount(rs.getInt("SeatCount"));
                vehicle.setDescription(rs.getString("Description"));
                vehicle.setTags(rs.getString("Tags"));

                list.add(vehicle);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}