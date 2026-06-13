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

    // === TOÀN BỘ FORM SQL ĐƯỢC QUY QUẨN LÊN ĐẦU CLASS ===
    private static final String LOGIN = "SELECT * FROM Account WHERE Email=? AND PasswordHash=?";

    private static final String CHECK_EMAIL = "SELECT Email FROM Account WHERE Email = ?";

    private static final String REGISTER = "INSERT INTO Account (RoleName, Email, PasswordHash, FullName, PhoneNumber, Status, CreatedAt, UpdatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_PASSWORD = "UPDATE Account SET PasswordHash = ?, UpdatedAt = ? WHERE Email = ?";

    private static final String LOG_EMAIL = "INSERT INTO EmailLog (CampaignID, RecipientAccountID, Subject, Status, SentAt) VALUES (?, ?, ?, ?, ?)";

    private static final String GET_ACCOUNT_ID = "SELECT AccountID FROM Account WHERE Email = ?";

    private static final String CHANGE_PASSWORD = "UPDATE Account SET PasswordHash = ?, UpdatedAt = ? WHERE Email = ? AND PasswordHash = ?";

    private static final String HASH_PASSWORD = "SELECT * FROM Account WHERE Email = ?";

    private static final String INSERT_DOC = "INSERT INTO IdentityDocument (OwnerAccountID, OwnerType, DocType, NationalID, SecureFileUrl, Status, UploadedAt) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String ACCEPT_DRIVER_TERMS = "UPDATE Driver SET terms_accepted = 1, TermsAcceptedAt = ? WHERE AccountID = ?";

    private static final String GET_DRIVER_PROFILE
            = "SELECT a.AccountID, a.Email, a.FullName, a.PhoneNumber, "
            + "       d.DriverID, d.ApprovalStatus, d.AvailabilityStatus, d.AverageRating, d.WalletBalance, d.terms_accepted, "
            + "       i.DocumentID, i.DocType, i.SecureFileUrl, i.Status AS DocStatus "
            + "FROM Account a "
            + "LEFT JOIN Driver d ON a.AccountID = d.AccountID "
            + "LEFT JOIN IdentityDocument i ON a.AccountID = i.OwnerAccountID "
            + "WHERE a.AccountID = ? AND a.RoleName = 'Driver'";

    private static final String UPDATE_ACCOUNT_INFO = "UPDATE Account SET FullName = ?, PhoneNumber = ?, UpdatedAt = ? WHERE AccountID = ?";

    private static final String UPDATE_DRIVER_STATUS = "UPDATE Driver SET AvailabilityStatus = ? WHERE AccountID = ?";

private static final String REUPLOAD_BOTH_DOCUMENTS = 
    "UPDATE IdentityDocument SET SecureFileUrl = ?, Status = 'Pending', UploadedAt = ? " +
    "WHERE OwnerAccountID = ? AND DocType = ?";

    public Account checkLogin(String email, String password) throws SQLException {
        Account account = null;
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                // 📑 THỬ NGHIỆM THẾ HỆ CŨ: Chạy câu lệnh LOGIN gốc của bạn (Có 2 dấu hỏi)
                ptm = conn.prepareStatement(LOGIN);
                ptm.setString(1, email);
                ptm.setString(2, password); // Truyền tham số số 2 bình thường, KHÔNG CÒN LỖI tham số số 2 nữa
                rs = ptm.executeQuery();

                if (rs.next()) {
                    // 👉 Nếu tìm thấy dòng dữ liệu: Đây là tài khoản DATA SAMPLE (Mật khẩu chữ thô)
                    String roleName = rs.getString("RoleName");
                    String userEmail = rs.getString("Email");
                    String fullName = rs.getString("FullName");
                    String phoneNumber = rs.getString("PhoneNumber");
                    String status = rs.getString("Status");
                    Timestamp createdAt = rs.getTimestamp("CreatedAt");
                    Timestamp updatedAt = rs.getTimestamp("UpdatedAt");

                    account = new Account(roleName, userEmail, "***", fullName, phoneNumber, status, createdAt, updatedAt);
                } else {
                    // 🔄 CHUYỂN HƯỚNG THẾ HỆ MỚI: Nếu câu lệnh trên không ra kết quả, thử check theo luồng BCrypt
                    // Đóng ResultSet và Statement cũ để giải phóng tài nguyên trước khi nạp lệnh mới
                    if (rs != null) {
                        rs.close();
                    }
                    if (ptm != null) {
                        ptm.close();
                    }

                    // Tạo một câu truy vấn nhanh chỉ lọc theo Email để bốc chuỗi băm BCrypt lên
                    String backupQuery = "SELECT * FROM Account WHERE Email = ?";
                    ptm = conn.prepareStatement(backupQuery);
                    ptm.setString(1, email);
                    rs = ptm.executeQuery();

                    if (rs.next()) {
                        String dbHashedPassword = rs.getString("PasswordHash");

                        // Tiến hành đối chiếu kiểm tra qua thư viện BCrypt
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
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
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
                ptm = conn.prepareStatement(HASH_PASSWORD); // Dùng lại form truy vấn theo Email có sẵn
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
     * ĐĂNG KÝ TÀI XẾ: Chèn đồng thời Account và 2 tài liệu hồ sơ sử dụng cơ chế
     * Transaction an toàn
     */
    public boolean registerAccountWithDocs(Account acc, String cccdUrl, String licenseUrl) throws SQLException {
        boolean isCreated = false;
        Connection conn = null;
        PreparedStatement ptmAcc = null;
        PreparedStatement ptmDoc = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                // 💡 BƯỚC CHỐT: Tắt cơ chế Auto-Commit để bắt đầu quản lý Transaction thủ công
                conn.setAutoCommit(false);

                // 1. Chèn dữ liệu cơ bản vào bảng Account và yêu cầu lấy về ID tự tăng vừa sinh ra
                ptmAcc = conn.prepareStatement(REGISTER, java.sql.Statement.RETURN_GENERATED_KEYS);
                ptmAcc.setString(1, acc.getRoleName());
                ptmAcc.setString(2, acc.getEmail());
                ptmAcc.setString(3, acc.getHashPassword());
                ptmAcc.setString(4, acc.getFullName());
                ptmAcc.setString(5, acc.getPhoneNumber());
                ptmAcc.setString(6, "Active");
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ptmAcc.setTimestamp(7, now);
                ptmAcc.setTimestamp(8, now);

                int affectedRows = ptmAcc.executeUpdate();

                if (affectedRows > 0) {
                    // Đọc khóa tự tăng AccountID từ bảng Account
                    rs = ptmAcc.getGeneratedKeys();
                    if (rs.next()) {
                        int generatedAccountId = rs.getInt(1);

                        // Khởi tạo PreparedStatement cho bảng tài liệu hồ sơ
                        ptmDoc = conn.prepareStatement(INSERT_DOC);

                        // 2. Thiết lập dòng dữ liệu thứ nhất: Ảnh CCCD (NationalID)
                        ptmDoc.setInt(1, generatedAccountId);
                        ptmDoc.setString(2, "Driver");
                        ptmDoc.setString(3, "NationalID");
                        ptmDoc.setNull(4, java.sql.Types.VARCHAR); // Để trống số định danh thô để Admin tự cập nhật khi duyệt
                        ptmDoc.setString(5, cccdUrl);
                        ptmDoc.setString(6, "Pending"); // Trạng thái chờ duyệt duyệt hồ sơ
                        ptmDoc.setTimestamp(7, now);
                        ptmDoc.addBatch(); // Xếp vào hàng đợi gom lệnh

                        // 3. Thiết lập dòng dữ liệu thứ hai: Ảnh Bằng lái xe (DriverLicense)
                        ptmDoc.setInt(1, generatedAccountId);
                        ptmDoc.setString(2, "Driver");
                        ptmDoc.setString(3, "DriverLicense");
                        ptmDoc.setNull(4, java.sql.Types.VARCHAR);
                        ptmDoc.setString(5, licenseUrl);
                        ptmDoc.setString(6, "Pending");
                        ptmDoc.setTimestamp(7, now);
                        ptmDoc.addBatch(); // Xếp vào hàng đợi gom lệnh

                        // Kích nổ lưu cả 2 tệp hồ sơ cùng lúc xuống cơ sở dữ liệu
                        int[] docResults = ptmDoc.executeBatch();

                        if (docResults.length == 2) {
                            // ✅ Mọi luồng thông suốt -> Chốt hạ ghi vĩnh viễn vào ổ đĩa DB
                            conn.commit();
                            isCreated = true;
                        } else {
                            conn.rollback(); // Gặp lỗi nạp batch tài liệu -> Hoàn tác toàn bộ
                        }
                    }
                } else {
                    conn.rollback(); // Lỗi tạo Account -> Hoàn tác toàn bộ
                }
            }
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Có bất kỳ Exception hệ thống nào xảy ra -> Hủy bỏ giao dịch lập tức
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            throw new SQLException("Transaction Register Driver Error: " + e.getMessage());
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ptmDoc != null) {
                ptmDoc.close();
            }
            if (ptmAcc != null) {
                ptmAcc.close();
            }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Trả lại trạng thái vận hành mặc định cho luồng kết nối Pool
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                conn.close();
            }
        }
        return isCreated;
    }

    /**
     * XÁC NHẬN ĐIỀU KHOẢN: Cập nhật trạng thái đồng ý (1) và thời gian đồng ý
     * cho Tài xế
     */
    public boolean acceptDriverTerms(int accountId) throws SQLException {
        boolean isUpdated = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                ptm = conn.prepareStatement(ACCEPT_DRIVER_TERMS);

                // Lấy mốc thời gian thực tế hiện tại
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

    /**
     * LẤY PROFILE TÀI XẾ: Gom thông tin từ bảng Account, Driver và danh sách
     * IdentityDocument
     */
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
                    // Khởi tạo map chứa thông tin chung ở dòng đầu tiên đọc được
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
                        profile.put("documents", documents); // Gắn danh sách list vào object tổng
                    }

                    // Đọc danh sách giấy tờ đi kèm (nếu có)
                    int docId = rs.getInt("DocumentID");
                    if (docId > 0) { // Nếu có giấy tờ nộp kèm
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

    /**
     * CẬP NHẬT PROFILE TÀI XẾ: Cập nhật đồng thời bảng Account và bảng Driver
     * sử dụng Transaction
     */
    public boolean updateDriverProfile(int accountId, String fullName, String phoneNumber, String availabilityStatus) throws SQLException {
        boolean isUpdated = false;
        Connection conn = null;
        PreparedStatement ptmAcc = null;
        PreparedStatement ptmDrv = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                // 💡 KÍCH HOẠT TRANSACTION
                conn.setAutoCommit(false);
                Timestamp now = new Timestamp(System.currentTimeMillis());

                // 1. Cập nhật bảng Account (Họ tên, Số điện thoại)
                ptmAcc = conn.prepareStatement(UPDATE_ACCOUNT_INFO);
                ptmAcc.setString(1, fullName);
                ptmAcc.setString(2, phoneNumber);
                ptmAcc.setTimestamp(3, now);
                ptmAcc.setInt(4, accountId);
                int accRows = ptmAcc.executeUpdate();

                // 2. Cập nhật bảng Driver (Trạng thái hoạt động)
                ptmDrv = conn.prepareStatement(UPDATE_DRIVER_STATUS);
                ptmDrv.setString(1, availabilityStatus);
                ptmDrv.setInt(2, accountId);
                int drvRows = ptmDrv.executeUpdate();

                // Nếu cả hai bảng đều được cập nhật thành công thành công
                if (accRows > 0 && drvRows > 0) {
                    conn.commit(); // ✅ Xác nhận lưu vĩnh viễn vào DB
                    isUpdated = true;
                } else {
                    conn.rollback(); // ❌ Hoàn tác nếu một trong hai bảng lỗi
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

    /**
     * UPLOAD LẠI GIẤY TỜ: Cập nhật link ảnh mới và đưa trạng thái về 'Pending'
     */
public boolean reuploadBothDocuments(int accountId, String cccdUrl, String licenseUrl) throws SQLException {
    boolean isSuccess = false;
    Connection conn = null;
    PreparedStatement ptm = null;
    try {
        conn = DbUtils.getConnection();
        if (conn != null) {
            // 💡 KÍCH HOẠT TRANSACTION THỦ CÔNG
            conn.setAutoCommit(false);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            
            ptm = conn.prepareStatement(REUPLOAD_BOTH_DOCUMENTS);
            
            // 1. Gom lệnh dòng 1: Cập nhật lại ảnh CCCD (NationalID)
            ptm.setString(1, cccdUrl);
            ptm.setTimestamp(2, now);
            ptm.setInt(3, accountId);
            ptm.setString(4, "NationalID");
            ptm.addBatch(); // Xếp vào hàng chờ số 1
            
            // 2. Gom lệnh dòng 2: Cập nhật lại ảnh Bằng lái (DriverLicense)
            ptm.setString(1, licenseUrl);
            ptm.setTimestamp(2, now);
            ptm.setInt(3, accountId);
            ptm.setString(4, "DriverLicense");
            ptm.addBatch(); // Xếp vào hàng chờ số 2
            
            // Kích nổ chạy cả 2 lệnh UPDATE xuống SQL Server
            int[] results = ptm.executeBatch();
            
            // Đảm bảo cả 2 dòng dữ liệu đều được tác động thành công
            if (results.length == 2) {
                conn.commit(); // ✅ Xác nhận lưu vĩnh viễn vào DB
                isSuccess = true;
            } else {
                conn.rollback(); // ❌ Hủy bỏ nếu có bất kỳ dòng nào bị hụt
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

}
