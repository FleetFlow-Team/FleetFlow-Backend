package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import utils.DbUtils;

public class NotificationDAO {

    public void insert(int recipientAccountId, Integer bookingId, String title, String message, String type) throws Exception {
        String sql = "INSERT INTO Notification "
                + "(RecipientAccountID, BookingID, Title, Message, Type, Channel, IsRead, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, 'IN_APP', 0, GETDATE())";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recipientAccountId);
            if (bookingId != null) {
                ps.setInt(2, bookingId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setString(5, type);
            ps.executeUpdate();
        }
    }

    public int resolveCustomerAccountByBookingId(int bookingId) throws Exception {
        String sql = "SELECT a.AccountID FROM Booking b "
                + "JOIN Customer c ON c.CustomerID = b.CustomerID "
                + "JOIN Account a ON a.AccountID = c.AccountID "
                + "WHERE b.BookingID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return -1;
            }
        }
    }

    public int resolveDriverAccountByBookingId(int bookingId) throws Exception {
        String sql = "SELECT a.AccountID FROM DriverJobBroadcast djb "
                + "JOIN Driver d ON d.DriverID = djb.AssignedDriverID "
                + "JOIN Account a ON a.AccountID = d.AccountID "
                + "WHERE djb.BookingID = ? AND djb.Status = 'ACCEPTED'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return -1;
            }
        }
    }

    public int resolveCustomerAccountByCustomerId(int customerId) throws Exception {
        String sql = "SELECT AccountID FROM Customer WHERE CustomerID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("AccountID");
                }
                return -1;
            }
        }
    }

    public List<Integer> getDispatcherAccountIds() throws Exception {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT AccountID FROM Account "
                + "WHERE RoleName = 'Dispatcher' AND (IsDeleted = 0 OR IsDeleted IS NULL)";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("AccountID"));
            }
        }
        return ids;
    }
}