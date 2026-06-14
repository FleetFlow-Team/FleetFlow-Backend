package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import model.BookingSummary;
import model.Customer;
import utils.DbUtils;

/**
 * DAO cho Customer Portal: BE-3 (profile), BE-4 (update profile), BE-7 (booking history).
 * Lưu ý: phần lịch sử đặt xe đặt tại đây (không sửa BookingDAO của bạn cùng nhóm)
 * vì đây là dữ liệu thuộc về khách hàng.
 */
public class CustomerDAO {

    private static final String GET_PROFILE_BY_EMAIL =
            "SELECT a.AccountID, a.Email, a.FullName, a.PhoneNumber, a.RoleName, a.Status, "
          + "c.CustomerID, c.Address, c.DebtBalance, c.BookingStatus, c.CreatedAt "
          + "FROM Account a "
          + "JOIN Customer c ON c.AccountID = a.AccountID "
          + "WHERE a.Email = ?";

    private static final String GET_CUSTOMER_ID_BY_EMAIL =
            "SELECT c.CustomerID FROM Customer c "
          + "JOIN Account a ON a.AccountID = c.AccountID "
          + "WHERE a.Email = ?";

    // COALESCE(?, col): tham số null => giữ nguyên giá trị cũ (hỗ trợ cập nhật từng phần)
    private static final String UPDATE_ACCOUNT =
            "UPDATE Account SET FullName = COALESCE(?, FullName), "
          + "PhoneNumber = COALESCE(?, PhoneNumber), UpdatedAt = ? WHERE Email = ?";

    private static final String UPDATE_CUSTOMER_ADDRESS =
            "UPDATE c SET Address = COALESCE(?, Address) "
          + "FROM Customer c JOIN Account a ON a.AccountID = c.AccountID WHERE a.Email = ?";

    private static final String GET_HISTORY_BY_CUSTOMER_ID =
            "SELECT b.BookingID, b.Status, b.BookingType, b.TripDirection, b.CreatedAt, "
          + "v.Brand, v.Model, v.LicensePlate, "
          + "bd.PickupAddress, bd.DropoffAddress, bd.DistanceKm, bd.DepartureTime, "
          + "bp.EstimatedTotal "
          + "FROM Booking b "
          + "JOIN Vehicle v ON v.VehicleID = b.VehicleID "
          + "LEFT JOIN BookingDetail bd ON bd.BookingID = b.BookingID "
          + "LEFT JOIN BookingPricing bp ON bp.BookingID = b.BookingID "
          + "WHERE b.CustomerID = ? "
          + "ORDER BY b.CreatedAt DESC";

    /** BE-3: lấy hồ sơ khách hàng theo email (từ JWT). Trả null nếu không phải khách hàng. */
    public Customer getProfileByEmail(String email) throws Exception {
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_PROFILE_BY_EMAIL)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Customer c = new Customer();
                    c.setAccountId(rs.getInt("AccountID"));
                    c.setEmail(rs.getString("Email"));
                    c.setFullName(rs.getString("FullName"));
                    c.setPhoneNumber(rs.getString("PhoneNumber"));
                    c.setRoleName(rs.getString("RoleName"));
                    c.setStatus(rs.getString("Status"));
                    c.setCustomerId(rs.getInt("CustomerID"));
                    c.setAddress(rs.getString("Address"));
                    c.setDebtBalance(rs.getBigDecimal("DebtBalance"));
                    c.setBookingStatus(rs.getString("BookingStatus"));
                    c.setCreatedAt(rs.getTimestamp("CreatedAt"));
                    return c;
                }
                return null;
            }
        }
    }

    /** Lấy CustomerID theo email; trả 0 nếu không có hồ sơ khách hàng. */
    public int getCustomerIdByEmail(String email) throws Exception {
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_CUSTOMER_ID_BY_EMAIL)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("CustomerID");
                }
                return 0;
            }
        }
    }

    /**
     * BE-4: cập nhật FullName, PhoneNumber (Account) + Address (Customer) trong 1 transaction.
     * Tham số nào null thì giữ nguyên giá trị cũ (nhờ COALESCE).
     * @return true nếu tìm thấy & cập nhật được tài khoản theo email.
     */
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
            if (psAccount != null) psAccount.close();
            if (psCustomer != null) psCustomer.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /** BE-7: lịch sử đặt xe của 1 khách hàng, mới nhất trước. */
    public List<BookingSummary> findBookingHistoryByCustomerId(int customerId) throws Exception {
        List<BookingSummary> list = new ArrayList<>();
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_HISTORY_BY_CUSTOMER_ID)) {

            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BookingSummary s = new BookingSummary();
                    s.setBookingId(rs.getInt("BookingID"));
                    s.setStatus(rs.getString("Status"));
                    s.setBookingType(rs.getString("BookingType"));
                    s.setTripDirection(rs.getString("TripDirection"));
                    s.setCreatedAt(rs.getTimestamp("CreatedAt"));
                    s.setBrand(rs.getString("Brand"));
                    s.setModel(rs.getString("Model"));
                    s.setLicensePlate(rs.getString("LicensePlate"));
                    s.setPickupAddress(rs.getString("PickupAddress"));
                    s.setDropoffAddress(rs.getString("DropoffAddress"));
                    s.setDistanceKm(rs.getBigDecimal("DistanceKm"));
                    s.setDepartureTime(rs.getTimestamp("DepartureTime"));
                    s.setEstimatedTotal(rs.getBigDecimal("EstimatedTotal"));
                    list.add(s);
                }
            }
        }
        return list;
    }
}
