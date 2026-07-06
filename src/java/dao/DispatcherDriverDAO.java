package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import utils.DbUtils;

/**
 * Truy vấn phục vụ Dispatcher xem danh sách tài xế (deadline #4): tình trạng
 * hoạt động, trạng thái duyệt, đánh giá TB, và số chuyến đã nhận/đã hoàn thành.
 */
public class DispatcherDriverDAO {

    public static class DriverRow {
        public int driverId;
        public int accountId;
        public String fullName;
        public String email;
        public String phoneNumber;
        public String availabilityStatus;
        public BigDecimal averageRating;
        public String accountStatus;
        public int tripsAccepted;
        public int tripsCompleted;
    }

    public List<DriverRow> listDriversWithStats() throws Exception {
        List<DriverRow> list = new ArrayList<>();
        String sql =
              "SELECT d.DriverID, d.AccountID, a.FullName, a.Email, a.PhoneNumber, "
            + "       d.AvailabilityStatus, d.AverageRating, a.Status AS AccountStatus, "
            + "       (SELECT COUNT(*) FROM DriverJobBroadcast b "
            + "          WHERE b.AssignedDriverID = d.DriverID AND b.Status = 'ACCEPTED') AS TripsAccepted, "
            + "       (SELECT COUNT(*) FROM DriverJobBroadcast b2 "
            + "          JOIN Booking bk ON bk.BookingID = b2.BookingID "
            + "          WHERE b2.AssignedDriverID = d.DriverID AND b2.Status = 'ACCEPTED' "
            + "            AND bk.Status = 'COMPLETED') AS TripsCompleted "
            + "FROM Driver d JOIN Account a ON a.AccountID = d.AccountID "
            + "WHERE (d.IsDeleted = 0 OR d.IsDeleted IS NULL) "
            + "ORDER BY d.DriverID";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DriverRow r = new DriverRow();
                r.driverId = rs.getInt("DriverID");
                r.accountId = rs.getInt("AccountID");
                r.fullName = rs.getString("FullName");
                r.email = rs.getString("Email");
                r.phoneNumber = rs.getString("PhoneNumber");
                r.availabilityStatus = rs.getString("AvailabilityStatus");
                r.averageRating = rs.getBigDecimal("AverageRating");
                r.accountStatus = rs.getString("AccountStatus");
                r.tripsAccepted = rs.getInt("TripsAccepted");
                r.tripsCompleted = rs.getInt("TripsCompleted");
                list.add(r);
            }
        }
        return list;
    }
}
