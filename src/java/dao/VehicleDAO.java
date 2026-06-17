/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
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