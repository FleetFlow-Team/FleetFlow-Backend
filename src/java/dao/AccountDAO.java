package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import model.Account;
import utils.DbUtils;

public class AccountDAO {

    // Thay đổi thành SELECT * để lấy chính xác và đầy đủ mọi thuộc tính của Account
    private static final String LOGIN = "SELECT * FROM Account WHERE Email=? AND PasswordHash=?";
    private static final String CHECK_EMAIL = "SELECT Email FROM Account WHERE Email = ?";
    private static final String REGISTER = "INSERT INTO Account (RoleName, Email, PasswordHash, FullName, PhoneNumber, Status, CreatedAt, UpdatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SAVE_TOKEN = "UPDATE Account SET ResetToken = ?, UpdatedAt = ? WHERE Email = ?";

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
                    // Đọc chính xác dữ liệu từ các cột của bảng Account
                    String roleName = rs.getString("RoleName");
                    String userEmail = rs.getString("Email");
                    String fullName = rs.getString("FullName");
                    String phoneNumber = rs.getString("PhoneNumber");
                    String status = rs.getString("Status");
                    Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
                    
                    // Khởi tạo Object khớp hoàn toàn với cấu trúc Constructor của Model Account
                    account = new Account(roleName, userEmail, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // In lỗi ra Console của Server (Tomcat) để dễ kiểm tra nếu có biến động
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

    public boolean saveResetToken(String email, String token) throws SQLException {
        boolean isSaved = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(SAVE_TOKEN);
                ptm.setString(1, token);
                ptm.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ptm.setString(3, email);

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isSaved = true;
                }
            }
        } catch (Exception e) {
            throw new SQLException("Database Update Token Error: " + e.getMessage());
        } finally {
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return isSaved;
    }
}