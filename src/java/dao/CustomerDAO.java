package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

public class CustomerDAO {

    private static final String GET_PROFILE_BY_EMAIL
            = "SELECT a.AccountID, a.Email, a.FullName, a.PhoneNumber, a.RoleName, a.Status, "
            + "c.CustomerID, c.Address, c.DebtBalance, c.BookingStatus, c.CreatedAt "
            + "FROM Account a "
            + "JOIN Customer c ON c.AccountID = a.AccountID "
            + "WHERE a.Email = ?";

    private static final String GET_CUSTOMER_ID_BY_EMAIL
            = "SELECT c.CustomerID FROM Customer c "
            + "JOIN Account a ON a.AccountID = c.AccountID "
            + "WHERE a.Email = ?";

    private static final String UPDATE_ACCOUNT
            = "UPDATE Account SET FullName = COALESCE(?, FullName), "
            + "PhoneNumber = COALESCE(?, PhoneNumber), UpdatedAt = ? WHERE Email = ?";

    private static final String UPDATE_CUSTOMER_ADDRESS
            = "UPDATE c SET Address = COALESCE(?, Address) "
            + "FROM Customer c JOIN Account a ON a.AccountID = c.AccountID WHERE a.Email = ?";

    private static final String GET_HISTORY_BY_CUSTOMER_ID
            = "SELECT b.BookingID, b.Status, b.BookingType, b.TripDirection, b.CreatedAt, "
            + "v.Brand, v.Model, v.LicensePlate, "
            + "bd.PickupAddress, bd.DropoffAddress, bd.DistanceKm, bd.DepartureTime, "
            + "bp.EstimatedTotal "
            + "FROM Booking b "
            + "JOIN Vehicle v ON v.VehicleID = b.VehicleID "
            + "LEFT JOIN BookingDetail bd ON bd.BookingID = b.BookingID "
            + "LEFT JOIN BookingPricing bp ON bp.BookingID = b.BookingID "
            + "WHERE b.CustomerID = ? "
            + "ORDER BY b.CreatedAt DESC";

    public Map<String, Object> getProfileByEmail(String email) throws Exception {
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(GET_PROFILE_BY_EMAIL)) {
            ps.setString(1, email);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("accountId", rs.getInt("AccountID"));
                    m.put("customerId", rs.getInt("CustomerID"));
                    m.put("email", rs.getString("Email"));
                    m.put("fullName", rs.getString("FullName"));
                    m.put("phoneNumber", rs.getString("PhoneNumber"));
                    m.put("roleName", rs.getString("RoleName"));
                    m.put("status", rs.getString("Status"));
                    m.put("address", rs.getString("Address"));
                    m.put("debtBalance", rs.getBigDecimal("DebtBalance"));
                    m.put("bookingStatus", rs.getString("BookingStatus"));
                    m.put("createdAt", rs.getTimestamp("CreatedAt"));
                    return m;
                }
                return null;
            }
        }
    }

    public int getCustomerIdByEmail(String email) throws Exception {
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(GET_CUSTOMER_ID_BY_EMAIL)) {
            ps.setString(1, email);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("CustomerID");
                }
                return 0;
            }
        }
    }

    public boolean updateProfileByEmail(String email, String fullName, String phoneNumber, String address)
            throws Exception {
        Connection conn = null;
        PreparedStatement psAccount = null;
        PreparedStatement psCustomer = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            psAccount = conn.prepareStatement(UPDATE_ACCOUNT);
            psAccount.setString(1, fullName);
            psAccount.setString(2, phoneNumber);
            psAccount.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            psAccount.setString(4, email);
            int affected = psAccount.executeUpdate();

            psCustomer = conn.prepareStatement(UPDATE_CUSTOMER_ADDRESS);
            psCustomer.setString(1, address);
            psCustomer.setString(2, email);
            psCustomer.executeUpdate();

            conn.commit();
            return affected > 0;
        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (psAccount != null) {
                psAccount.close();
            }
            if (psCustomer != null) {
                psCustomer.close();
            }
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public List<Map<String, Object>> findBookingHistoryByCustomerId(int customerId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(GET_HISTORY_BY_CUSTOMER_ID)) {
            ps.setInt(1, customerId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("bookingId", rs.getInt("BookingID"));
                    m.put("status", rs.getString("Status"));
                    m.put("bookingType", rs.getString("BookingType"));
                    m.put("tripDirection", rs.getString("TripDirection"));
                    m.put("createdAt", rs.getTimestamp("CreatedAt"));
                    m.put("brand", rs.getString("Brand"));
                    m.put("model", rs.getString("Model"));
                    m.put("licensePlate", rs.getString("LicensePlate"));
                    m.put("pickupAddress", rs.getString("PickupAddress"));
                    m.put("dropoffAddress", rs.getString("DropoffAddress"));
                    m.put("distanceKm", rs.getBigDecimal("DistanceKm"));
                    m.put("departureTime", rs.getTimestamp("DepartureTime"));
                    m.put("estimatedTotal", rs.getBigDecimal("EstimatedTotal"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    public Integer getCustomerIdByAccountId(int accountId) {
        String sql = "SELECT CustomerID FROM Customer WHERE AccountID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("CustomerID");
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("getCustomerIdByAccountId error: " + e.getMessage());
        }
        return null;
    }

    public Integer getAccountIdByCustomerId(int customerId) {
        String sql = "SELECT AccountID FROM Customer WHERE CustomerID = ?";
        try (Connection conn = DbUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("AccountID");
            }
        } catch (SQLException | ClassNotFoundException e) {
            System.err.println("getAccountIdByCustomerId error: " + e.getMessage());
        }
        return null;
    }

   
}
