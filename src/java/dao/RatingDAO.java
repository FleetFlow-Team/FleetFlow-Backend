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
}