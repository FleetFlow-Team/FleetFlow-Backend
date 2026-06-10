package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import model.Account;
import utils.DbUtils;

public class AccountDAO {

    // === TOÀN BỘ FORM SQL ĐƯỢC QUY QUẨN LÊN ĐẦU CLASS ===
    private static final String LOGIN = "SELECT * FROM Account WHERE Email=? AND PasswordHash=?";
    private static final String CHECK_EMAIL = "SELECT Email FROM Account WHERE Email = ?";
    private static final String REGISTER = "INSERT INTO Account (RoleName, Email, PasswordHash, FullName, PhoneNumber, Status, CreatedAt, UpdatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_PASSWORD = "UPDATE Account SET PasswordHash = ?, UpdatedAt = ? WHERE Email = ?";
    private static final String LOG_EMAIL = "INSERT INTO EmailLog (CampaignID, RecipientAccountID, Subject, Status, SentAt) VALUES (?, ?, ?, ?, ?)";
    private static final String GET_ACCOUNT_ID = "SELECT AccountID FROM Account WHERE Email = ?";
    private static final String CHANGE_PASSWORD = "UPDATE Account SET PasswordHash = ?, UpdatedAt = ? WHERE Email = ? AND PasswordHash = ?";
   
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
                    String roleName = rs.getString("RoleName");
                    String userEmail = rs.getString("Email");
                    String fullName = rs.getString("FullName");
                    String phoneNumber = rs.getString("PhoneNumber");
                    String status = rs.getString("Status");
                    Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
                    
                    account = new Account(roleName, userEmail, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); 
            throw new SQLException("Error at checkLogin: " + e.getMessage());
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
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
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return isExist;
    }

    public boolean registerAccount(Account acc) throws SQLException {
        boolean isCreated = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(REGISTER);
                ptm.setString(1, acc.getRoleName() != null ? acc.getRoleName() : "Customer");
                ptm.setString(2, acc.getEmail());
                ptm.setString(3, acc.getHashPassword()); 
                ptm.setString(4, acc.getFullName());
                ptm.setString(5, acc.getPhoneNumber());
                ptm.setString(6, acc.getStatus() != null ? acc.getStatus() : "Active");
                ptm.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                ptm.setTimestamp(8, new Timestamp(System.currentTimeMillis()));

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isCreated = true;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Database Insertion Error: " + e.getMessage());
        } finally {
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return isCreated;
    }

    public boolean updatePassword(String email, String newPassword) throws SQLException {
        boolean isUpdated = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(UPDATE_PASSWORD);
                ptm.setString(1, newPassword); 
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
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
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
                
                if (campaignID != null) ptm.setInt(1, campaignID); else ptm.setNull(1, java.sql.Types.INTEGER);
                if (recipientAccountID != null) ptm.setInt(2, recipientAccountID); else ptm.setNull(2, java.sql.Types.INTEGER);
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
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
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
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
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
            ptm.setString(1, newPassword); // Mật khẩu mới
            ptm.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            ptm.setString(3, email);
            ptm.setString(4, oldPassword); // Mật khẩu cũ (bắt buộc phải khớp thì SQL mới update)

            int row = ptm.executeUpdate();
            if (row > 0) {
                isChanged = true;
            }
        }
    } catch (Exception e) {
        throw new SQLException("Database Change Password Error: " + e.getMessage());
    } finally {
        if (ptm != null) ptm.close();
        if (conn != null) conn.close();
    }
    return isChanged;
}
}
