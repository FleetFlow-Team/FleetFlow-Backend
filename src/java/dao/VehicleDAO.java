package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.Vehicle;
import model.VehicleAIData;
import utils.DbUtils;

/**
 *
 * @author User
 */
public class VehicleDAO {

    // Câu SELECT dùng chung cho BE-5 và BE-6: Vehicle + VehicleType + gộp Tag.
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

    /**
     * BE-5 — GET /api/v1/vehicles
     * Danh sách xe CÓ THỂ ĐẶT (Status = Available), lọc tùy chọn theo số chỗ và/hoặc loại xe.
     * Loại bỏ xe Maintenance/Unavailable theo BR-25 & BR-26.
     *
     * @param seatCount lọc theo số chỗ (null = bỏ qua)
     * @param typeId    lọc theo VehicleTypeID (null = bỏ qua)
     */
    public List<Vehicle> findAvailable(Integer seatCount, Integer typeId) throws Exception {
        List<Vehicle> list = new ArrayList<>();

        StringBuilder sql = new StringBuilder(BASE_SELECT)
                .append("WHERE UPPER(v.Status) = 'AVAILABLE' "); // an toàn với cả 'Available' lẫn 'AVAILABLE'

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

    /**
     * BE-6 — GET /api/v1/vehicles/{id}
     * Chi tiết 1 xe theo VehicleID (kèm loại xe + danh sách tag). Trả null nếu không có.
     */
    public Vehicle findById(int vehicleId) throws Exception {
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

    private Vehicle mapVehicle(ResultSet rs) throws Exception {
        Vehicle v = new Vehicle();
        v.setVehicleId(rs.getInt("VehicleID"));
        v.setVehicleTypeId(rs.getInt("VehicleTypeID"));
        v.setTypeName(rs.getString("TypeName"));
        v.setLicensePlate(rs.getString("LicensePlate"));
        v.setChassisNumber(rs.getString("ChassisNumber"));
        v.setEngineNumber(rs.getString("EngineNumber"));
        v.setBrand(rs.getString("Brand"));
        v.setModel(rs.getString("Model"));
        v.setSeatCount(rs.getInt("SeatCount"));
        v.setStatus(rs.getString("Status"));
        int km = rs.getInt("AccumulatedKm");
        v.setAccumulatedKm(rs.wasNull() ? null : km);
        v.setDescription(rs.getString("Description"));
        v.setTags(rs.getString("Tags"));
        return v;
    }

    // ===================== GIỮ NGUYÊN HÀM CHO AI (đang được GeminiService dùng) =====================
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
