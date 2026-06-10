/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.VehicleAIData;
import utils.DbUtils;
/**
 *
 * @author User
 */
public class VehicleDAO {
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
