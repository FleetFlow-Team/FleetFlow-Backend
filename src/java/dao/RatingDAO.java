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
}
