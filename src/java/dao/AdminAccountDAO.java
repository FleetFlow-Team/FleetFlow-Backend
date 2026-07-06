package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import utils.DbUtils;

/**
 * Truy vấn phục vụ Admin quản lý tài khoản Driver/Dispatcher (khóa/mở khóa + xem
 * danh sách). Thao tác lock/unlock thực tế tái dùng {@link CustomerLockDAO}
 * (set Account.Status) vì chung một cơ chế theo AccountID.
 */
public class AdminAccountDAO {

    public static class AccountRow {
        public int accountId;
        public String fullName;
        public String email;
        public String phoneNumber;
        public String roleName;
        public String status;
    }

    /** Lấy RoleName của 1 account; null nếu không tồn tại. */
    public String getRole(int accountId) throws Exception {
        String sql = "SELECT RoleName FROM Account WHERE AccountID = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("RoleName") : null;
            }
        }
    }

    /** Danh sách account theo role (Driver | Dispatcher) kèm trạng thái khóa. */
    public List<AccountRow> listByRole(String roleName) throws Exception {
        List<AccountRow> list = new ArrayList<>();
        String sql = "SELECT AccountID, FullName, Email, PhoneNumber, RoleName, Status "
                + "FROM Account WHERE LOWER(RoleName) = LOWER(?) "
                + "AND (IsDeleted = 0 OR IsDeleted IS NULL) ORDER BY AccountID";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AccountRow r = new AccountRow();
                    r.accountId = rs.getInt("AccountID");
                    r.fullName = rs.getString("FullName");
                    r.email = rs.getString("Email");
                    r.phoneNumber = rs.getString("PhoneNumber");
                    r.roleName = rs.getString("RoleName");
                    r.status = rs.getString("Status");
                    list.add(r);
                }
            }
        }
        return list;
    }
}
