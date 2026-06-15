package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Driver; // 🚀 Import Model Driver thật vào xử lý
import utils.DbUtils;

public class DriverDAO {

    // =========================================================================
    // ==================== SQL FORM ĐỒNG BỘ 100% CỜ ISDELETED ===================
    // =========================================================================
    private static final String ACCEPT_DRIVER_TERMS = "UPDATE Driver SET terms_accepted = 1, TermsAcceptedAt = ?, UpdatedAt = ? WHERE AccountID = ? AND IsDeleted = 0";
    private static final String UPDATE_ACCOUNT_INFO = "UPDATE Account SET FullName = ?, PhoneNumber = ?, UpdatedAt = ? WHERE AccountID = ? AND IsDeleted = 0";
    private static final String UPDATE_DRIVER_STATUS = "UPDATE Driver SET AvailabilityStatus = ?, UpdatedAt = ? WHERE AccountID = ? AND IsDeleted = 0";
    private static final String INSERT_IDENTITY_DOC = "INSERT INTO IdentityDocument (OwnerAccountID, OwnerType, DocType, NationalID, SecureFileUrl, Status, UploadedAt, IsDeleted) VALUES (?, 'Driver', ?, NULL, ?, 'Pending', ?, 0)";
    private static final String REUPLOAD_BOTH_DOCUMENTS = "UPDATE IdentityDocument SET SecureFileUrl = ?, Status = 'Pending', UploadedAt = ?, RejectReason = NULL WHERE OwnerAccountID = ? AND DocType = ? AND IsDeleted = 0";
    private static final String CHECK_DOC_TYPE_EXIST = "SELECT 1 FROM IdentityDocument WHERE OwnerAccountID = ? AND DocType = ? AND IsDeleted = 0";
    
    private static final String COUNT_DRIVER_DOCUMENTS
            = "SELECT COUNT(DISTINCT DocType) FROM IdentityDocument "
            + "WHERE OwnerAccountID = ? AND DocType IN ('NationalID', 'DriverLicense') "
            + "AND SecureFileUrl IS NOT NULL AND SecureFileUrl <> '' AND IsDeleted = 0";

    // Truy vấn dữ liệu Driver chuẩn chỉnh theo Model
    private static final String GET_DRIVER_OBJECT_PROFILE
            = "SELECT AccountID, DriverID, ApprovalStatus, AvailabilityStatus, TermsAcceptedAt, AverageRating, WalletBalance, terms_accepted, CreatedAt, UpdatedAt "
            + "FROM Driver WHERE AccountID = ? AND IsDeleted = 0";

    private static final String GET_DRIVER_STATUS_AND_ID = 
        "SELECT DriverID, ApprovalStatus, AvailabilityStatus FROM Driver WHERE AccountID = ? AND IsDeleted = 0";

    private static final String DRIVER_TOTAL_EARNINGS = 
        "SELECT SUM(NetAmount) FROM DriverEarning WHERE DriverID = ? AND EarningType = 'Trip' AND IsDeleted = 0";

    private static final String DRIVER_COMPLETED_TRIPS = 
        "SELECT COUNT(*) FROM DriverEarning WHERE DriverID = ? AND EarningType = 'Trip' AND IsDeleted = 0";

    private static final String DRIVER_CANCELLATION_COMPENSATION = 
        "SELECT SUM(NetAmount) FROM DriverEarning WHERE DriverID = ? AND EarningType = 'CancellationCompensation' AND IsDeleted = 0";

    private static final String GET_DRIVER_DOCUMENTS_LIST = 
        "SELECT DocType, NationalID, SecureFileUrl, Status, UploadedAt, RejectReason FROM IdentityDocument WHERE OwnerAccountID = ? AND OwnerType = 'Driver' AND IsDeleted = 0";

    // =========================================================================
    // ============================ HÀM NGHIỆP VỤ DRIVER ========================
    // =========================================================================

    public boolean submitDriverDocuments(int accountId, String cccdUrl, String licenseUrl) throws SQLException {
        boolean isSubmitted = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                conn.setAutoCommit(false);
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ptm = conn.prepareStatement(INSERT_IDENTITY_DOC);

                ptm.setInt(1, accountId);
                ptm.setString(2, "NationalID");
                ptm.setString(3, cccdUrl);
                ptm.setTimestamp(4, now);
                ptm.addBatch();

                ptm.setInt(1, accountId);
                ptm.setString(2, "DriverLicense");
                ptm.setString(3, licenseUrl);
                ptm.setTimestamp(4, now);
                ptm.addBatch();

                int[] results = ptm.executeBatch();
                if (results.length == 2) {
                    conn.commit();
                    isSubmitted = true;
                } else {
                    conn.rollback();
                }
            }
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            throw new SQLException("Submit Driver Documents Error: " + e.getMessage());
        } finally {
            if (ptm != null) ptm.close();
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
                conn.close();
            }
        }
        return isSubmitted;
    }

    public boolean acceptDriverTerms(int accountId) throws SQLException {
        boolean isUpdated = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(ACCEPT_DRIVER_TERMS);
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ptm.setTimestamp(1, now); // Cập nhật ngày đồng ý điều khoản
                ptm.setTimestamp(2, now); // Cập nhật cột UpdatedAt mới thêm của Driver
                ptm.setInt(3, accountId);

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isUpdated = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at acceptDriverTerms: " + e.getMessage());
        } finally {
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return isUpdated;
    }

    /**
     * 🚀 HÀM ĐƯỢC THAY ĐỔI: Trả về trực tiếp thực thể Model Driver thay vì Map lỏng lẻo
     */
    public Driver getDriverProfile(int accountId) throws SQLException {
        Driver driver = null;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(GET_DRIVER_OBJECT_PROFILE);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();

                if (rs.next()) {
                    driver = new Driver();
                    driver.setAccountId(rs.getInt("AccountID"));
                    driver.setApprovalStatus(rs.getString("ApprovalStatus"));
                    driver.setAvailabilityStatus(rs.getString("AvailabilityStatus"));
                    driver.setTermsAcceptedAt(rs.getTimestamp("TermsAcceptedAt"));
                    driver.setAverageRating(rs.getBigDecimal("AverageRating"));
                    driver.setWalletBalance(rs.getBigDecimal("WalletBalance"));
                    driver.setTermsAccepted(rs.getBoolean("terms_accepted")); // Đọc dữ liệu biến boolean mới
                    driver.setCreatedAt(rs.getTimestamp("CreatedAt"));
                    driver.setUpdatedAt(rs.getTimestamp("UpdatedAt"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverProfile: " + e.getMessage());
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return driver;
    }

    public boolean updateDriverProfile(int accountId, String fullName, String phoneNumber, String availabilityStatus) throws SQLException {
        boolean isUpdated = false;
        Connection conn = null;
        PreparedStatement ptmAcc = null;
        PreparedStatement ptmDrv = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                conn.setAutoCommit(false);
                Timestamp now = new Timestamp(System.currentTimeMillis());

                ptmAcc = conn.prepareStatement(UPDATE_ACCOUNT_INFO);
                ptmAcc.setString(1, fullName);
                ptmAcc.setString(2, phoneNumber);
                ptmAcc.setTimestamp(3, now);
                ptmAcc.setInt(4, accountId);
                int accRows = ptmAcc.executeUpdate();

                ptmDrv = conn.prepareStatement(UPDATE_DRIVER_STATUS);
                ptmDrv.setString(1, availabilityStatus);
                ptmDrv.setTimestamp(2, now); // Cập nhật cột Driver.UpdatedAt
                ptmDrv.setInt(3, accountId);
                int drvRows = ptmDrv.executeUpdate();

                if (accRows > 0 && drvRows > 0) {
                    conn.commit();
                    isUpdated = true;
                } else {
                    conn.rollback();
                }
            }
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            throw new SQLException("Transaction Update Driver Profile Error: " + e.getMessage());
        } finally {
            if (ptmDrv != null) ptmDrv.close();
            if (ptmAcc != null) ptmAcc.close();
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
                conn.close();
            }
        }
        return isUpdated;
    }

    public boolean reuploadBothDocuments(int accountId, String cccdUrl, String licenseUrl) throws SQLException {
        boolean isSuccess = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                conn.setAutoCommit(false);
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ptm = conn.prepareStatement(REUPLOAD_BOTH_DOCUMENTS);

                ptm.setString(1, cccdUrl);
                ptm.setTimestamp(2, now);
                ptm.setInt(3, accountId);
                ptm.setString(4, "NationalID");
                ptm.addBatch();

                ptm.setString(1, licenseUrl);
                ptm.setTimestamp(2, now);
                ptm.setInt(3, accountId);
                ptm.setString(4, "DriverLicense");
                ptm.addBatch();

                int[] results = ptm.executeBatch();
                if (results.length == 2) {
                    conn.commit();
                    isSuccess = true;
                } else {
                    conn.rollback();
                }
            }
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            e.printStackTrace();
            throw new SQLException("Transaction Reupload Both Docs Error: " + e.getMessage());
        } finally {
            if (ptm != null) ptm.close();
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
                conn.close();
            }
        }
        return isSuccess;
    }

    public boolean isDriverDocumentsComplete(int accountId) throws SQLException {
        boolean isComplete = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(COUNT_DRIVER_DOCUMENTS);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 2) {
                        isComplete = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at isDriverDocumentsComplete: " + e.getMessage());
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return isComplete;
    }

    public boolean isDocTypeExist(int accountId, String docType) throws SQLException {
        boolean isExist = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(CHECK_DOC_TYPE_EXIST);
                ptm.setInt(1, accountId);
                ptm.setString(2, docType);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    isExist = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at isDocTypeExist: " + e.getMessage());
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return isExist;
    }

    public Map<String, Object> getDriverDashboardMetrics(int accountId) throws SQLException {
        Map<String, Object> metrics = new HashMap<>();
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                int driverId = -1;
                ptm = conn.prepareStatement(GET_DRIVER_STATUS_AND_ID);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    driverId = rs.getInt("DriverID");
                    metrics.put("approvalStatus", rs.getString("ApprovalStatus"));
                    metrics.put("availabilityStatus", rs.getString("AvailabilityStatus"));
                }
                rs.close(); ptm.close();

                if (driverId == -1) return null;

                ptm = conn.prepareStatement(DRIVER_TOTAL_EARNINGS);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                metrics.put("totalEarnings", rs.next() ? rs.getDouble(1) : 0.0);
                rs.close(); ptm.close();

                ptm = conn.prepareStatement(DRIVER_COMPLETED_TRIPS);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                metrics.put("completedTrips", rs.next() ? rs.getInt(1) : 0);
                rs.close(); ptm.close();

                ptm = conn.prepareStatement(DRIVER_CANCELLATION_COMPENSATION);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                metrics.put("cancellationCompensation", rs.next() ? rs.getDouble(1) : 0.0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverDashboardMetrics: " + e.getMessage());
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return metrics;
    }

    public Map<String, Object> getDriverApprovalDetail(int accountId) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(GET_DRIVER_STATUS_AND_ID);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    result.put("driverId", rs.getInt("DriverID"));
                    result.put("approvalStatus", rs.getString("ApprovalStatus"));
                } else {
                    return null;
                }
                rs.close(); ptm.close();

                ptm = conn.prepareStatement(GET_DRIVER_DOCUMENTS_LIST);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                while (rs.next()) {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("docType", rs.getString("DocType"));
                    doc.put("nationalId", rs.getString("NationalID"));
                    doc.put("secureFileUrl", rs.getString("SecureFileUrl"));
                    doc.put("status", rs.getString("Status"));
                    doc.put("uploadedAt", rs.getTimestamp("UploadedAt"));
                    doc.put("rejectReason", rs.getString("RejectReason")); // 🚀 Đọc trường RejectReason mới thêm từ DB
                    documents.add(doc);
                }
                result.put("documents", documents);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverApprovalDetail: " + e.getMessage());
        } finally {
            if (rs != null) rs.close();
            if (ptm != null) ptm.close();
            if (conn != null) conn.close();
        }
        return result;
    }
}