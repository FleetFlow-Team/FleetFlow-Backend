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
    private static final String ACCEPT_DRIVER_TERMS = "UPDATE Driver SET terms_accepted = 1, TermsAcceptedAt = ? WHERE AccountID = ?";
    private static final String UPDATE_ACCOUNT_INFO = "UPDATE Account SET FullName = ?, PhoneNumber = ?, UpdatedAt = ? WHERE AccountID = ?";
    private static final String UPDATE_DRIVER_STATUS = "UPDATE Driver SET AvailabilityStatus = ? WHERE AccountID = ?";

    // 🛠️ SQL Hệ thống rẽ nhánh nghiệp vụ theo yêu cầu của Sếp
    private static final String INSERT_CUSTOMER = "INSERT INTO Customer (AccountID, Address, DebtBalance, BookingStatus, CreatedAt) VALUES (?, ?, 0, 'Active', ?)";
    private static final String INSERT_DRIVER = "INSERT INTO Driver (AccountID, ApprovalStatus, AvailabilityStatus, TermsAcceptedAt, AverageRating, WalletBalance, CreatedAt, terms_accepted) VALUES (?, 'Pending', 'Offline', NULL, NULL, 0, ?, 0)";
    private static final String INSERT_IDENTITY_DOC = "INSERT INTO IdentityDocument (OwnerAccountID, OwnerType, DocType, NationalID, SecureFileUrl, Status, UploadedAt) VALUES (?, 'Driver', ?, NULL, ?, 'Pending', ?)";
    private static final String REUPLOAD_BOTH_DOCUMENTS = "UPDATE IdentityDocument SET SecureFileUrl = ?, Status = 'Pending', UploadedAt = ? WHERE OwnerAccountID = ? AND DocType = ?";
    private static final String COUNT_DRIVER_DOCUMENTS = "SELECT COUNT(DISTINCT DocType) FROM IdentityDocument WHERE OwnerAccountID = ? AND DocType IN ('NationalID', 'DriverLicense')";
    private static final String GET_DRIVER_PROFILE
            = "SELECT a.AccountID, a.Email, a.FullName, a.PhoneNumber, "
            + "       d.DriverID, d.ApprovalStatus, d.AvailabilityStatus, d.AverageRating, d.WalletBalance, d.terms_accepted, "
            + "       i.DocumentID, i.DocType, i.SecureFileUrl, i.Status AS DocStatus "
            + "FROM Account a "
            + "LEFT JOIN Driver d ON a.AccountID = d.AccountID "
            + "LEFT JOIN IdentityDocument i ON a.AccountID = i.OwnerAccountID "
            + "WHERE a.AccountID = ? AND a.RoleName = 'Driver'";
    private static final String CHECK_DOC_TYPE_EXIST = "SELECT 1 FROM IdentityDocument WHERE OwnerAccountID = ? AND DocType = ?";
//XÓA TÀI KHOAN TEST
    private static final String DELETE_IDENTITY_DOCS = "DELETE FROM IdentityDocument WHERE OwnerAccountID = ?";
    private static final String DELETE_DRIVER = "DELETE FROM Driver WHERE AccountID = ?";
    private static final String DELETE_CUSTOMER = "DELETE FROM Customer WHERE AccountID = ?";
    private static final String DELETE_ACCOUNT = "DELETE FROM Account WHERE AccountID = ?"; // Thay đổi tên cột cho đúng với DB của bạn
    private static final String DELETE_EMAIL_LOGS = "DELETE FROM EmailLog WHERE RecipientAccountID = ?";
    // =========================================================================
    // ======================== PHÂN HỆ XỬ LÝ LOGIC NGHIỆP VỤ =========================
    // =========================================================================

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
     * 🚖 2. NỘP HỒ SƠ GIẤY TỜ RIÊNG BIỆT CHO TÀI XẾ (SAU KHI ĐÃ CÓ TÀI KHOẢN)
     * Chèn đồng thời ảnh CCCD và Bằng lái vào bảng IdentityDocument sử dụng
     * Batch Processing
     */
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

                // Gom lệnh dòng 1: Nộp ảnh CCCD
                ptm.setInt(1, accountId);
                ptm.setString(2, "NationalID");
                ptm.setString(3, cccdUrl);
                ptm.setTimestamp(4, now);
                ptm.addBatch();

                // Gom lệnh dòng 2: Nộp ảnh Bằng lái
                ptm.setInt(1, accountId);
                ptm.setString(2, "DriverLicense");
                ptm.setString(3, licenseUrl);
                ptm.setTimestamp(4, now);
                ptm.addBatch();

                int[] results = ptm.executeBatch();
                if (results.length == 2) {
                    conn.commit(); // ✅ Xác nhận lưu dữ liệu thành công
                    isSubmitted = true;
                } else {
                    conn.rollback(); // ❌ Hoàn tác nếu có lỗi nạp tệp
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
            throw new SQLException("Submit Driver Documents Error: " + e.getMessage());
        } finally {
            if (ptm != null) {
                ptm.close();
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
                ptm.setTimestamp(1, now);
                ptm.setInt(2, accountId);

                int row = ptm.executeUpdate();
                if (row > 0) {
                    isUpdated = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at acceptDriverTerms: " + e.getMessage());
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

    public Map<String, Object> getDriverProfile(int accountId) throws SQLException {
        Map<String, Object> profile = null;
        java.util.List<Map<String, Object>> documents = new java.util.ArrayList<>();

        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(GET_DRIVER_PROFILE);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();

                while (rs.next()) {
                    if (profile == null) {
                        profile = new HashMap<>();
                        profile.put("accountID", rs.getInt("AccountID"));
                        profile.put("email", rs.getString("Email"));
                        profile.put("fullName", rs.getString("FullName"));
                        profile.put("phoneNumber", rs.getString("PhoneNumber"));
                        profile.put("driverID", rs.getInt("DriverID"));
                        profile.put("approvalStatus", rs.getString("ApprovalStatus"));
                        profile.put("availabilityStatus", rs.getString("AvailabilityStatus"));
                        profile.put("averageRating", rs.getDouble("AverageRating"));
                        profile.put("walletBalance", rs.getDouble("WalletBalance"));
                        profile.put("termsAccepted", rs.getBoolean("terms_accepted"));
                        profile.put("documents", documents);
                    }

                    int docId = rs.getInt("DocumentID");
                    if (docId > 0) {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("documentID", docId);
                        doc.put("docType", rs.getString("DocType"));
                        doc.put("secureFileUrl", rs.getString("SecureFileUrl"));
                        doc.put("status", rs.getString("DocStatus"));
                        documents.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverProfile: " + e.getMessage());
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
        return profile;
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
                ptmDrv.setInt(2, accountId);
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
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            throw new SQLException("Transaction Update Driver Profile Error: " + e.getMessage());
        } finally {
            if (ptmDrv != null) {
                ptmDrv.close();
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
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            throw new SQLException("Transaction Reupload Both Docs Error: " + e.getMessage());
        } finally {
            if (ptm != null) {
                ptm.close();
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
        return isSuccess;
    }

    /**
     * KIỂM TRA ĐỦ HỒ SƠ: Đếm xem tài xế đã nộp đủ cả 2 loại giấy tờ bắt buộc
     * chưa
     */
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
                    if (count == 2) { // Đã nộp đủ cả 2 loại bắt buộc
                        isComplete = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at isDriverDocumentsComplete: " + e.getMessage());
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
        return isComplete;
    }

    /**
     * XÓA TÀI KHOẢN: Dọn dẹp tài khoản test và các dữ liệu liên quan (Đã sửa
     * lỗi khóa ngoại EmailLog + Driver + Customer)
     */
    public boolean deleteAccount(int accountId) throws SQLException {
        Connection conn = null;
        PreparedStatement ptm = null;
        boolean isDeleted = false;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                // Bật transaction để quản lý tập trung bảo vệ dữ liệu
                conn.setAutoCommit(false);

                // Giai đoạn 1: Quét sạch lịch sử gửi email (bảng con)
                ptm = conn.prepareStatement(DELETE_EMAIL_LOGS);
                ptm.setInt(1, accountId);
                ptm.executeUpdate();
                ptm.close();

                // Giai đoạn 2: Quét sạch giấy tờ tài xế (bảng con nếu có)
                ptm = conn.prepareStatement(DELETE_IDENTITY_DOCS);
                ptm.setInt(1, accountId);
                ptm.executeUpdate();
                ptm.close();

                // Giai đoạn 3: Quét sạch thông tin tài xế (bảng con nếu có)
                ptm = conn.prepareStatement(DELETE_DRIVER);
                ptm.setInt(1, accountId);
                ptm.executeUpdate();
                ptm.close();

                // 🚀 GIAI ĐOẠN 4 MỚI BỔ SUNG: Quét sạch thông tin khách hàng (bảng con nếu có)
                ptm = conn.prepareStatement(DELETE_CUSTOMER);
                ptm.setInt(1, accountId);
                ptm.executeUpdate();
                ptm.close();

                // Giai đoạn cuối: Chốt hạ xóa tài khoản gốc trong bảng Account
                ptm = conn.prepareStatement(DELETE_ACCOUNT);
                ptm.setInt(1, accountId);
                int affectedRows = ptm.executeUpdate();

                if (affectedRows > 0) {
                    conn.commit(); // ✅ Thành công sạch sẽ, chốt hạ ghi xuống ổ đĩa
                    isDeleted = true;
                } else {
                    conn.rollback();
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
            throw new SQLException("Error at deleteAccount: " + e.getMessage());
        } finally {
            if (ptm != null) {
                ptm.close();
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
        return isDeleted;
    }

    /**
     * KIỂM TRA GIẤY TỜ ĐÃ TỒN TẠI: Check xem tài xế đã từng nộp loại giấy tờ
     * này chưa
     */
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
                ptm.setString(2, docType); // "NationalID" hoặc "DriverLicense"
                rs = ptm.executeQuery();
                if (rs.next()) {
                    isExist = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at isDocTypeExist: " + e.getMessage());
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
}
