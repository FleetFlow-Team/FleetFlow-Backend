package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import model.Account;
import model.Driver;
import model.IdentityDocument;
import utils.DbUtils;

public class DriverVerificationDAO {

    // SQL
    private static final String GET_PENDING_DRIVERS
            = "SELECT a.AccountID, a.FullName, a.Email, a.PhoneNumber, a.CreatedAt, "
            + "       d.DriverID, d.ApprovalStatus "
            + "FROM Account a "
            + "JOIN Driver d ON d.AccountID = a.AccountID "
            + "WHERE d.ApprovalStatus = 'PENDING' "
            + "ORDER BY a.CreatedAt ASC";

    private static final String GET_DOCS_BY_ACCOUNT
            = "SELECT DocumentID, OwnerAccountID, DocType, SecureFileUrl, Status, UploadedAt "
            + "FROM IdentityDocument "
            + "WHERE OwnerAccountID = ? AND OwnerType = 'DRIVER'";

    private static final String APPROVE_DRIVER
            = "UPDATE Driver SET ApprovalStatus = 'APPROVED', UpdatedAt = ? "
            + "WHERE AccountID = ? AND ApprovalStatus = 'PENDING'";

    private static final String APPROVE_DOCS
            = "UPDATE IdentityDocument SET Status = 'APPROVED', VerifiedAt = ?, VerifiedBy = ? "
            + "WHERE OwnerAccountID = ? AND OwnerType = 'DRIVER'";

    private static final String REJECT_DRIVER
            = "UPDATE Driver SET ApprovalStatus = 'REJECTED', UpdatedAt = ? "
            + "WHERE AccountID = ? AND ApprovalStatus = 'PENDING'";

    private static final String REJECT_DOCS
            = "UPDATE IdentityDocument "
            + "SET Status = 'REJECTED', RejectReason = ?, UpdatedAt = ? "
            + "WHERE OwnerAccountID = ? AND OwnerType = 'DRIVER'";

    // BE-13: Lấy danh sách driver PENDING kèm account info
    public List<Account> getPendingDriverAccounts() throws SQLException, ClassNotFoundException {
        List<Account> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            ps = conn.prepareStatement(GET_PENDING_DRIVERS);
            rs = ps.executeQuery();
            while (rs.next()) {
                Account acc = new Account();
                acc.setId(rs.getInt("AccountID"));
                acc.setFullName(rs.getString("FullName"));
                acc.setEmail(rs.getString("Email"));
                acc.setPhoneNumber(rs.getString("PhoneNumber"));
                acc.setCreatedAt(rs.getTimestamp("CreatedAt"));
                list.add(acc);
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return list;
    }

    // Lấy giấy tờ theo accountId
    public List<IdentityDocument> getDocsByAccountId(int accountId) throws SQLException, ClassNotFoundException {
        List<IdentityDocument> docs = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            ps = conn.prepareStatement(GET_DOCS_BY_ACCOUNT);
            ps.setInt(1, accountId);
            rs = ps.executeQuery();
            while (rs.next()) {
                IdentityDocument doc = new IdentityDocument();
                doc.setId(rs.getInt("DocumentID"));
                doc.setOwnerAccountId(rs.getInt("OwnerAccountID"));
                doc.setDocType(rs.getString("DocType"));
                doc.setSecureFileUrl(rs.getString("SecureFileUrl"));
                doc.setStatus(rs.getString("Status"));
                doc.setUploadedAt(rs.getTimestamp("UploadedAt"));
                docs.add(doc);
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return docs;
    }

    // BE-14: Duyệt driver — dùng transaction
    public boolean approveDriver(int accountId, int adminAccountId) throws SQLException, ClassNotFoundException, ClassNotFoundException {
        Connection conn = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            Timestamp now = new Timestamp(System.currentTimeMillis());

            PreparedStatement psDriver = conn.prepareStatement(APPROVE_DRIVER);
            psDriver.setTimestamp(1, now);
            psDriver.setInt(2, accountId);
            int rows = psDriver.executeUpdate();
            psDriver.close();

            if (rows == 0) {
                conn.rollback();
                return false; // không tìm thấy hoặc không phải PENDING
            }

            PreparedStatement psDocs = conn.prepareStatement(APPROVE_DOCS);
            psDocs.setTimestamp(1, now);
            psDocs.setInt(2, adminAccountId);
            psDocs.setInt(3, accountId);
            psDocs.executeUpdate();
            psDocs.close();

            conn.commit();
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // BE-15: Từ chối driver — dùng transaction
    public boolean rejectDriver(int accountId, String rejectReason) throws SQLException, ClassNotFoundException, ClassNotFoundException {
        Connection conn = null;
        try {
            conn = DbUtils.getConnection();
            conn.setAutoCommit(false);

            Timestamp now = new Timestamp(System.currentTimeMillis());

            PreparedStatement psDriver = conn.prepareStatement(REJECT_DRIVER);
            psDriver.setTimestamp(1, now);
            psDriver.setInt(2, accountId);
            int rows = psDriver.executeUpdate();
            psDriver.close();

            if (rows == 0) {
                conn.rollback();
                return false;
            }

            PreparedStatement psDocs = conn.prepareStatement(REJECT_DOCS);
            psDocs.setString(1, rejectReason);
            psDocs.setTimestamp(2, now);
            psDocs.setInt(3, accountId);
            psDocs.executeUpdate();

            conn.commit();
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
}
