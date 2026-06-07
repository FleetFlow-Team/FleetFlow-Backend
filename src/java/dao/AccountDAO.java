/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import utils.DbUtils;
import model.Account;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
/**
 *
 * @author User
 */
public class AccountDAO {
    // Câu lệnh truy vấn khớp chính xác 100% tên bảng và tên cột trong file SQL của bạn
    private static final String LOGIN = "SELECT RoleName, Email, PasswordHash, FullName, PhoneNumber, Status, CreatedAt, UpdatedAt "
                                      + "FROM Account WHERE Email=? AND PasswordHash=?";

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
                    String fullName = rs.getString("FullName");
                    String phoneNumber = rs.getString("PhoneNumber");
                    String status = rs.getString("Status");
                    java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    java.sql.Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
                    
                    // Khởi tạo đối tượng Account dựa trên cấu trúc Constructor trong Model của bạn
                    // Mật khẩu che đi bằng "***" để bảo mật khi lưu vào Session
                    account = new Account(roleName, email, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Đóng tài nguyên nghiêm ngặt đúng format mẫu của bạn
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return account;
    }
}
