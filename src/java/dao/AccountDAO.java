package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import model.Account;
import utils.DbUtils;

public class AccountDAO {

    // =========================================================================
    // ================== TOÀN BỘ FORM SQL ĐƯỢC QUY QUẨN LÊN ĐẦU CLASS ==================
    // =========================================================================
    private static final String LOGIN = "SELECT * FROM Account WHERE Email=? AND PasswordHash=?";
    private static final String CHECK_EMAIL = "SELECT Email FROM Account WHERE Email = ?";
    private static final String REGISTER_ACCOUNT = "INSERT INTO Account (RoleName, Email, PasswordHash, FullName, PhoneNumber, Status, CreatedAt, UpdatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_PASSWORD = "UPDATE Account SET PasswordHash = ?, UpdatedAt = ? WHERE Email = ?";
    private static final String LOG_EMAIL = "INSERT INTO EmailLog (CampaignID, RecipientAccountID, Subject, Status, SentAt) VALUES (?, ?, ?, ?, ?)";
    private static final String GET_ACCOUNT_ID = "SELECT AccountID FROM Account WHERE Email = ?";
    private static final String CHANGE_PASSWORD = "UPDATE Account SET PasswordHash = ?, UpdatedAt = ? WHERE Email = ? AND PasswordHash = ?";
    private static final String HASH_PASSWORD = "SELECT * FROM Account WHERE Email = ?";
    private static final String INSERT_CUSTOMER = "INSERT INTO Customer (AccountID, Address, Status, CreatedAt) VALUES (?, ?, 'Active', ?)";
    private static final String INSERT_DRIVER = "INSERT INTO Driver (AccountID, ApprovalStatus, AvailabilityStatus, TermsAcceptedAt, AverageRating, WalletBalance, CreatedAt, TermsAccepted) VALUES (?, 'Pending', 'Offline', NULL, NULL, 0, ?, 0)";
   


    // =========================================================================
    // ======================== PHÂN HỆ XỬ LÝ LOGIC NGHIỆP VỤ =========================
    // =========================================================================
    private static final String FIND_BY_EMAIL = "SELECT * FROM Account WHERE Email = ?";

    public Account findByEmail(String email) throws SQLException, ClassNotFoundException {
        Account account = null;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            ptm = conn.prepareStatement(FIND_BY_EMAIL);
            ptm.setString(1, email);
            rs = ptm.executeQuery();

            if (rs.next()) {
                int accountId = rs.getInt("AccountID");
                String roleName = rs.getString("RoleName");
                String userEmail = rs.getString("Email");
                String fullName = rs.getString("FullName");
                String phoneNumber = rs.getString("PhoneNumber");
                String status = rs.getString("Status");
                Timestamp createdAt = rs.getTimestamp("CreatedAt");
                Timestamp updatedAt = rs.getTimestamp("UpdatedAt");

                account = new Account(roleName, userEmail, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                account.setId(accountId);
            }
            return account;
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
    }

    public Account checkLogin(String email, String password) throws SQLException {
        Account account = null;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(LOGIN);
                ptm.setString(1, email);
                ptm.setString(2, password);
                rs = ptm.executeQuery();

                if (rs.next()) {
                    int accountId = rs.getInt("AccountID");
                    String roleName = rs.getString("RoleName");
                    String userEmail = rs.getString("Email");
                    String fullName = rs.getString("FullName");
                    String phoneNumber = rs.getString("PhoneNumber");
                    String status = rs.getString("Status");
                    Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    Timestamp updatedAt = rs.getTimestamp("UpdatedAt");

                    account = new Account(roleName, userEmail, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                    account.setId(accountId);
                } else {
                    if (rs != null) {
                        rs.close();
                    }
                    if (ptm != null) {
                        ptm.close();
                    }

                    String backupQuery = "SELECT * FROM Account WHERE Email = ?";
                    ptm = conn.prepareStatement(backupQuery);
                    ptm.setString(1, email);
                    rs = ptm.executeQuery();

                    if (rs.next()) {
                        String dbHashedPassword = rs.getString("PasswordHash");

                        if (dbHashedPassword != null && dbHashedPassword.startsWith("$2a$")) {
                            if (utils.PasswordUtils.checkPassword(password, dbHashedPassword)) {
                                int accountId = rs.getInt("AccountID");
                                String roleName = rs.getString("RoleName");
                                String userEmail = rs.getString("Email");
                                String fullName = rs.getString("FullName");
                                String phoneNumber = rs.getString("PhoneNumber");
                                String status = rs.getString("Status");
                                Timestamp createdAt = rs.getTimestamp("CreatedAt");
                                Timestamp updatedAt = rs.getTimestamp("UpdatedAt");

                                account = new Account(roleName, userEmail, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                                account.setId(accountId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at checkLogin: " + e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return account;
    }

    public boolean checkEmailExist(String email) throws SQLException {
        boolean isExist = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(CHECK_EMAIL);
                ptm.setString(1, email);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    isExist = true;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Error at checkEmailExist: " + e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return isExist;
    }

    public boolean updatePassword(String email, String newHashedPassword) throws SQLException {
        boolean isUpdated = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(UPDATE_PASSWORD);
                ptm.setString(1, newHashedPassword);
                ptm.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ptm.setString(3, email);

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isUpdated = true;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Database Update Password Error: " + e.getMessage());
        } finally {
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return isUpdated;
    }

    public boolean logEmail(Integer campaignID, Integer recipientAccountID, String subject, String status) throws SQLException {
        boolean isLogged = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(LOG_EMAIL);

                if (campaignID != null) {
                    ptm.setInt(1, campaignID);
                } else {
                    ptm.setNull(1, java.sql.Types.INTEGER);
                }
                if (recipientAccountID != null) {
                    ptm.setInt(2, recipientAccountID);
                } else {
                    ptm.setNull(2, java.sql.Types.INTEGER);
                }
                ptm.setString(3, subject);
                ptm.setString(4, status);
                ptm.setTimestamp(5, new Timestamp(System.currentTimeMillis()));

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isLogged = true;
                }
            }
        } catch (Exception e) {
            System.err.println("LOG_ERROR: [AccountDAO] Lỗi chèn lịch sử vào bảng EmailLog: " + e.getMessage());
        } finally {
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return isLogged;
    }

    public int getAccountIdByEmail(String email) throws SQLException {
        int accountId = 0;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(GET_ACCOUNT_ID);
                ptm.setString(1, email);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    accountId = rs.getInt("AccountID");
                }
            }
        } catch (Exception e) {
            throw new SQLException("Error at getAccountIdByEmail: " + e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return accountId;
    }

    public boolean changePassword(String email, String oldPassword, String newPassword) throws SQLException {
        boolean isChanged = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(CHANGE_PASSWORD);
                ptm.setString(1, newPassword);
                ptm.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
                ptm.setString(3, email);
                ptm.setString(4, oldPassword);

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isChanged = true;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Database Change Password Error: " + e.getMessage());
        } finally {
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return isChanged;
    }

    public String getHashedPasswordByEmail(String email) throws SQLException {
        String dbHashedPassword = null;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(HASH_PASSWORD);
                ptm.setString(1, email);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    dbHashedPassword = rs.getString("PasswordHash");
                }
            }
        } catch (Exception e) {
            throw new SQLException("Error at getHashedPasswordByEmail: " + e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return dbHashedPassword;
    }

    /**
     * 🚀 1. ĐĂNG KÝ HỆ THỐNG GỘP (SWITCH-CASE LỒNG TRANSACTION THEO YÊU CẦU CỦA
     * SẾP) Tạo song song dữ liệu bảng Account cơ bản và bảng định danh vai trò
     * phụ trợ tương ứng.
     */
    public boolean registerUnifiedAccount(Account acc, String address) throws SQLException {
        boolean isCreated = false;
        Connection conn = null;
        PreparedStatement ptmAcc = null;
        PreparedStatement ptmRole = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                // Kích hoạt quản lý giao dịch Transaction thủ công
                conn.setAutoCommit(false);
                Timestamp now = new Timestamp(System.currentTimeMillis());

                // Thực thi chèn dòng dữ liệu vào bảng Account tổng trước
                ptmAcc = conn.prepareStatement(REGISTER_ACCOUNT, java.sql.Statement.RETURN_GENERATED_KEYS);
                ptmAcc.setString(1, acc.getRoleName());
                ptmAcc.setString(2, acc.getEmail());
                ptmAcc.setString(3, acc.getHashPassword());
                ptmAcc.setString(4, acc.getFullName());
                ptmAcc.setString(5, acc.getPhoneNumber());
                ptmAcc.setString(6, "Active");
                ptmAcc.setTimestamp(7, now);
                ptmAcc.setTimestamp(8, now);

                int affectedRows = ptmAcc.executeUpdate();

                if (affectedRows > 0) {
                    rs = ptmAcc.getGeneratedKeys();
                    if (rs.next()) {
                        int generatedAccountId = rs.getInt(1);

                        // Phân rẽ nhánh nghiệp vụ switch-case lưu dữ liệu bảng phụ
                        switch (acc.getRoleName()) {
                            case "Customer":
                                ptmRole = conn.prepareStatement(INSERT_CUSTOMER);
                                ptmRole.setInt(1, generatedAccountId);
                                ptmRole.setString(2, (address != null) ? address.trim() : "");
                                ptmRole.setTimestamp(3, now);
                                break;

                            case "Driver":
                                ptmRole = conn.prepareStatement(INSERT_DRIVER);
                                ptmRole.setInt(1, generatedAccountId);
                                ptmRole.setTimestamp(2, now);
                                break;

                            default:
                                throw new SQLException("Vai trò hệ thống không được hỗ trợ xử lý: " + acc.getRoleName());
                        }

                        int roleRows = ptmRole.executeUpdate();
                        if (roleRows > 0) {
                            conn.commit(); // ✅ Mọi thứ thông suốt -> Chốt hạ ghi xuống DB
                            isCreated = true;
                        } else {
                            conn.rollback(); // ❌ Lỗi bảng phụ -> Hoàn tác
                        }
                    }
                } else {
                    conn.rollback(); // ❌ Lỗi bảng Account -> Hoàn tác
                }
            }
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            throw new SQLException("Unified Registration Error: " + e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ptmRole != null) {
                ptmRole.close();
            }
            if (ptmAcc != null) {
                ptmAcc.close();
            }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                conn.close();
            }
        }
        return isCreated;
    }

    /**
     * Lấy danh sách AccountID của tất cả Dispatcher đang ACTIVE — dùng để
     * gửi notification khi có booking được auto-dispatch cho driver.
     */
    public java.util.List<Integer> getActiveDispatcherAccountIds() throws SQLException, ClassNotFoundException {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        String sql = "SELECT AccountID FROM Account WHERE RoleName = 'Dispatcher' AND Status = 'ACTIVE'";
        try (Connection conn = utils.DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("AccountID"));
            }
        }
        return ids;
    }

    /**
     * Khóa 1 account bất kỳ theo AccountID — dùng chung cho Admin khóa
     * Driver/Dispatcher (không ràng buộc công nợ như Customer).
     */
    public void lockAccountById(int accountId) throws Exception {
        String sql = "UPDATE Account SET Status = 'LOCKED', UpdatedAt = ? WHERE AccountID = ?";
        try (Connection conn = utils.DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, accountId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Không tìm thấy account #" + accountId);
            }
        }
    }

    public void unlockAccountById(int accountId) throws Exception {
        String sql = "UPDATE Account SET Status = 'ACTIVE', UpdatedAt = ? WHERE AccountID = ?";
        try (Connection conn = utils.DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, accountId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Không tìm thấy account #" + accountId);
            }
        }
    }

    /**
     * Kiểm tra 1 account có đúng RoleName mong muốn không — dùng để chặn Admin
     * lock nhầm account khác vai trò (VD gọi /admin/drivers/{id}/lock nhưng id
     * đó lại là Customer).
     */
    public String getRoleNameByAccountId(int accountId) throws Exception {
        String sql = "SELECT RoleName FROM Account WHERE AccountID = ?";
        try (Connection conn = utils.DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("RoleName") : null;
            }
        }
    }

    /**
     * Danh sách toàn bộ Dispatcher — cho Admin xem để chọn tài khoản khóa/mở khóa.
     */
    public java.util.List<Map<String, Object>> getAllDispatchers() throws Exception {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        String sql = "SELECT AccountID, FullName, Email, PhoneNumber, Status, CreatedAt "
                + "FROM Account WHERE RoleName = 'Dispatcher' AND IsDeleted = 0 ORDER BY AccountID";
        try (Connection conn = utils.DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("accountId", rs.getInt("AccountID"));
                row.put("fullName", rs.getString("FullName"));
                row.put("email", rs.getString("Email"));
                row.put("phoneNumber", rs.getString("PhoneNumber"));
                row.put("status", rs.getString("Status"));
                row.put("createdAt", rs.getTimestamp("CreatedAt"));
                list.add(row);
            }
        }
        return list;
    }
}