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
    // 🎯 ĐÃ ĐỔI CỘT: terms_accepted -> TermsAccepted
    private static final String ACCEPT_DRIVER_TERMS = "UPDATE Driver SET TermsAccepted = 1, TermsAcceptedAt = ?, UpdatedAt = ? WHERE AccountID = ? AND IsDeleted = 0";
    private static final String UPDATE_ACCOUNT_INFO = "UPDATE Account SET FullName = ?, PhoneNumber = ?, UpdatedAt = ? WHERE AccountID = ? AND IsDeleted = 0";
    private static final String UPDATE_DRIVER_STATUS = "UPDATE Driver SET AvailabilityStatus = ?, UpdatedAt = ? WHERE AccountID = ? AND IsDeleted = 0";
    private static final String UPDATE_DRIVER_AVAILABILITY_BY_DRIVER_ID = "UPDATE Driver SET AvailabilityStatus = ?, UpdatedAt = ? WHERE DriverID = ? AND IsDeleted = 0";
    // 🎯 ĐÃ ĐỒNG BỘ: Thiết lập trạng thái mặc định IsDeleted = 0 khi chèn mới giấy tờ
    private static final String INSERT_IDENTITY_DOC
            = "INSERT INTO IdentityDocument (OwnerAccountID, OwnerType, DocType, NationalID, SecureFileUrl, Status, UploadedAt, IsDeleted) VALUES (?, 'Driver', ?, NULL, ?, 'Pending', ?, 0)";

    private static final String REUPLOAD_BOTH_DOCUMENTS = "UPDATE IdentityDocument SET SecureFileUrl = ?, Status = 'Pending', UploadedAt = ?, RejectReason = NULL WHERE OwnerAccountID = ? AND DocType = ? AND IsDeleted = 0";
    private static final String CHECK_DOC_TYPE_EXIST = "SELECT 1 FROM IdentityDocument WHERE OwnerAccountID = ? AND DocType = ? AND IsDeleted = 0";

    private static final String COUNT_DRIVER_DOCUMENTS
            = "SELECT COUNT(DISTINCT DocType) FROM IdentityDocument "
            + "WHERE OwnerAccountID = ? AND DocType IN ('NationalID', 'DriverLicense') "
            + "AND SecureFileUrl IS NOT NULL AND SecureFileUrl <> '' AND IsDeleted = 0";

    // 🎯 ĐÃ ĐỔI CỘT: terms_accepted -> TermsAccepted
    private static final String GET_DRIVER_OBJECT_PROFILE
            = "SELECT AccountID, DriverID, ApprovalStatus, AvailabilityStatus, TermsAcceptedAt, AverageRating, WalletBalance, TermsAccepted, CreatedAt, UpdatedAt "
            + "FROM Driver WHERE AccountID = ? AND IsDeleted = 0";

    private static final String GET_DRIVER_STATUS_AND_ID
            = "SELECT DriverID, ApprovalStatus, AvailabilityStatus FROM Driver WHERE AccountID = ? AND IsDeleted = 0";

    private static final String DRIVER_TOTAL_EARNINGS
            = "SELECT SUM(NetAmount) FROM DriverEarning WHERE DriverID = ? AND EarningType = 'Trip' AND IsDeleted = 0";

    private static final String DRIVER_COMPLETED_TRIPS
            = "SELECT COUNT(*) FROM DriverEarning WHERE DriverID = ? AND EarningType = 'Trip' AND IsDeleted = 0";

    private static final String DRIVER_CANCELLATION_COMPENSATION
            = "SELECT SUM(NetAmount) FROM DriverEarning WHERE DriverID = ? AND EarningType = 'CancellationCompensation' AND IsDeleted = 0";

    private static final String GET_DRIVER_DOCUMENTS_LIST
            = "SELECT DocType, NationalID, SecureFileUrl, Status, UploadedAt, RejectReason FROM IdentityDocument WHERE OwnerAccountID = ? AND OwnerType = 'Driver' AND IsDeleted = 0";

    // SQL XEM VÍ CÔNG NỢ
    private static final String GET_DRIVER_WALLET_INFO
            = "SELECT DriverID, WalletBalance, ApprovalStatus FROM Driver WHERE AccountID = ? AND IsDeleted = 0";

    private static final String GET_DRIVER_WALLET_HISTORY
            = "SELECT EarningID, BookingID, EarningType, FareShare, SurchargeShare, CancellationCompensation, CompanyCommission, NetAmount, CreatedAt "
            + "FROM DriverEarning WHERE DriverID = ? AND IsDeleted = 0 ORDER BY CreatedAt DESC";

    // SQL THỐNG KÊ THU NHẬP TÀI XẾ
    private static final String GET_INCOME_SUMMARY_OVERVIEW
            = "SELECT "
            + "    SUM(CASE WHEN EarningType = 'Trip' THEN NetAmount ELSE 0 END) AS TotalTripEarnings, "
            + "    SUM(CASE WHEN EarningType = 'CancellationCompensation' THEN NetAmount ELSE 0 END) AS TotalCancellationCompensation, "
            + "    SUM(CompanyCommission) AS TotalCommissionPaid, "
            + "    SUM(NetAmount) AS TotalNetIncome "
            + "FROM DriverEarning "
            + "WHERE DriverID = (SELECT DriverID FROM Driver WHERE AccountID = ? AND IsDeleted = 0) AND IsDeleted = 0";

    private static final String GET_INCOME_BY_MONTH
            = "SELECT "
            + "    MONTH(CreatedAt) AS EarningMonth, "
            + "    YEAR(CreatedAt) AS EarningYear, "
            + "    SUM(NetAmount) AS MonthlyNetIncome, "
            + "    COUNT(EarningID) AS TotalTransactions "
            + "FROM DriverEarning "
            + "WHERE DriverID = (SELECT DriverID FROM Driver WHERE AccountID = ? AND IsDeleted = 0) AND IsDeleted = 0 "
            + "GROUP BY YEAR(CreatedAt), MONTH(CreatedAt) "
            + "ORDER BY EarningYear DESC, EarningMonth DESC";

    // =========================================================================
    // ============================ HÀM NGHIỆP VỤ DRIVER ========================
    // =========================================================================
    /**
     * 🚀 ĐÃ CẬP NHẬT: Buộc tài xế nộp đồng thời cả 2 file ảnh. Thiếu 1 trong 2
     * hoặc rỗng hệ thống hủy ngay lập tức, không lưu dữ liệu rác.
     */
    public boolean submitDriverDocuments(int accountId, String cccdUrl, String licenseUrl) throws SQLException {
        if (cccdUrl == null || cccdUrl.trim().isEmpty() || licenseUrl == null || licenseUrl.trim().isEmpty()) {
            return false;
        }

        boolean isSubmitted = false;
        Connection conn = null;
        PreparedStatement ptm = null;
        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                conn.setAutoCommit(false); // Bật tính năng quản lý Transaction thủ công
                Timestamp now = new Timestamp(System.currentTimeMillis());
                ptm = conn.prepareStatement(INSERT_IDENTITY_DOC);

                // 1. Nạp lệnh hàng đợi 1: Ghi nhận NationalID (CCCD)
                ptm.setInt(1, accountId);
                ptm.setString(2, "NationalID");
                ptm.setString(3, cccdUrl.trim());
                ptm.setTimestamp(4, now);
                ptm.addBatch();

                // 2. Nạp lệnh hàng đợi 2: Ghi nhận DriverLicense (Bằng lái xe)
                ptm.setInt(1, accountId);
                ptm.setString(2, "DriverLicense");
                ptm.setString(3, licenseUrl.trim());
                ptm.setTimestamp(4, now);
                ptm.addBatch();

                int[] results = ptm.executeBatch(); // Kích hoạt bắn đồng thời cả 2 lệnh insert xuống DB
                if (results.length == 2) {
                    conn.commit(); // ✅ Thành công trọn vẹn không lỗi -> Xác nhận chốt ghi dữ liệu
                    isSubmitted = true;
                } else {
                    conn.rollback(); // ❌ Lỗi một trong hai -> Quay đầu hoàn tác sạch sẽ
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
            if (ptm != null) {
                ptm.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
        return isUpdated;
    }

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

                    // 🎯 ĐÃ CẬP NHẬT: Đọc chuẩn từ tên cột viết hoa mới dưới DB
                    driver.setTermsAccepted(rs.getBoolean("TermsAccepted"));

                    driver.setCreatedAt(rs.getTimestamp("CreatedAt"));
                    driver.setUpdatedAt(rs.getTimestamp("UpdatedAt"));
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
                ptmDrv.setTimestamp(2, now);
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
                rs.close();
                ptm.close();

                if (driverId == -1) {
                    return null;
                }

                ptm = conn.prepareStatement(DRIVER_TOTAL_EARNINGS);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                metrics.put("totalEarnings", rs.next() ? rs.getDouble(1) : 0.0);
                rs.close();
                ptm.close();

                ptm = conn.prepareStatement(DRIVER_COMPLETED_TRIPS);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                metrics.put("completedTrips", rs.next() ? rs.getInt(1) : 0);
                rs.close();
                ptm.close();

                ptm = conn.prepareStatement(DRIVER_CANCELLATION_COMPENSATION);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                metrics.put("cancellationCompensation", rs.next() ? rs.getDouble(1) : 0.0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverDashboardMetrics: " + e.getMessage());
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
                rs.close();
                ptm.close();

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
                    doc.put("rejectReason", rs.getString("RejectReason"));
                    documents.add(doc);
                }
                result.put("documents", documents);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverApprovalDetail: " + e.getMessage());
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
        return result;
    }

    public Map<String, Object> getDriverWallet(int accountId) throws SQLException {
        Map<String, Object> walletData = null;
        List<Map<String, Object>> transactionHistory = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                int driverId = -1;

                // 1. Lấy thông số số dư ví hiện tại của tài xế từ bảng Driver
                ptm = conn.prepareStatement(GET_DRIVER_WALLET_INFO);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    driverId = rs.getInt("DriverID");
                    walletData = new HashMap<>();
                    walletData.put("driverID", driverId);
                    walletData.put("walletBalance", rs.getBigDecimal("WalletBalance")); // Trả về BigDecimal chuẩn số tiền
                    walletData.put("approvalStatus", rs.getString("ApprovalStatus"));
                    walletData.put("transactions", transactionHistory); // Đóng gói mảng lịch sử vào trong ví
                }
                rs.close();
                ptm.close();

                // Nếu accountID truyền vào không tồn tại tài xế nào
                if (driverId == -1) {
                    return null;
                }

                // 2. Kéo danh sách lịch sử các cuốc xe tăng giảm tiền (DriverEarning) của tài xế đó
                ptm = conn.prepareStatement(GET_DRIVER_WALLET_HISTORY);
                ptm.setInt(1, driverId);
                rs = ptm.executeQuery();
                while (rs.next()) {
                    Map<String, Object> txn = new HashMap<>();
                    txn.put("earningID", rs.getInt("EarningID"));
                    txn.put("bookingID", rs.getInt("BookingID"));
                    txn.put("earningType", rs.getString("EarningType")); // 'Trip' hoặc 'CancellationCompensation'
                    txn.put("fareShare", rs.getDouble("FareShare"));
                    txn.put("surchargeShare", rs.getDouble("SurchargeShare"));
                    txn.put("cancellationCompensation", rs.getDouble("CancellationCompensation"));
                    txn.put("companyCommission", rs.getDouble("CompanyCommission"));
                    txn.put("netAmount", rs.getDouble("NetAmount")); // Số tiền thực nhận cộng vào ví
                    txn.put("createdAt", rs.getTimestamp("CreatedAt"));

                    transactionHistory.add(txn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverWallet: " + e.getMessage());
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
        return walletData;
    }

    public Map<String, Object> getDriverIncomeSummary(int accountId) throws SQLException {
        Map<String, Object> summary = new HashMap<>();
        List<Map<String, Object>> monthlyDetails = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ptm = null;
        ResultSet rs = null;

        try {
            conn = DbUtils.getConnection();
            if (conn != null) {
                // 1. Chạy câu lệnh 1: Lấy các số số liệu tổng quan (Tổng doanh thu, hoa hồng...)
                ptm = conn.prepareStatement(GET_INCOME_SUMMARY_OVERVIEW);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                if (rs.next()) {
                    // Nếu tài xế hoàn toàn chưa có cuốc xe nào (Hàm SUM trả về null), ta gán mặc định là 0.0
                    summary.put("totalTripEarnings", rs.getObject("TotalTripEarnings") != null ? rs.getDouble("TotalTripEarnings") : 0.0);
                    summary.put("totalCancellationCompensation", rs.getObject("TotalCancellationCompensation") != null ? rs.getDouble("TotalCancellationCompensation") : 0.0);
                    summary.put("totalCommissionPaid", rs.getObject("TotalCommissionPaid") != null ? rs.getDouble("TotalCommissionPaid") : 0.0);
                    summary.put("totalNetIncome", rs.getObject("TotalNetIncome") != null ? rs.getDouble("TotalNetIncome") : 0.0);
                }
                rs.close();
                ptm.close();

                // 2. Chạy câu lệnh 2: Nhóm doanh thu theo từng tháng/năm
                ptm = conn.prepareStatement(GET_INCOME_BY_MONTH);
                ptm.setInt(1, accountId);
                rs = ptm.executeQuery();
                while (rs.next()) {
                    Map<String, Object> monthData = new HashMap<>();
                    monthData.put("month", rs.getInt("EarningMonth"));
                    monthData.put("year", rs.getInt("EarningYear"));
                    monthData.put("monthlyNetIncome", rs.getDouble("MonthlyNetIncome"));
                    monthData.put("totalTransactions", rs.getInt("TotalTransactions"));
                    monthlyDetails.add(monthData);
                }
                summary.put("monthlyBreakdown", monthlyDetails);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("Error at getDriverIncomeSummary: " + e.getMessage());
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
        return summary;
    }

    /**
     * Lấy DriverID đơn giản từ AccountID — dùng cho các API mới liên quan tới
     * DriverJobBroadcast (driver xem/phản hồi lệnh dispatch). Trả về -1 nếu
     * không tìm thấy.
     */
    public int getDriverIdByAccountId(int accountId) throws SQLException, ClassNotFoundException {
        String sql = "SELECT DriverID FROM Driver WHERE AccountID = ? AND IsDeleted = 0";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("DriverID");
                }
                return -1;
            }
        }
    }

    /**
     * Lấy tên + SĐT driver từ DriverID — dùng để build nội dung notification
     * báo cho Dispatcher biết booking đã được driver nào nhận/từ chối. Trả về
     * null nếu không tìm thấy.
     */
    public Map<String, String> getDriverNameAndPhone(int driverId) throws SQLException, ClassNotFoundException {
        String sql = "SELECT a.FullName, a.PhoneNumber "
                + "FROM Driver d JOIN Account a ON d.AccountID = a.AccountID "
                + "WHERE d.DriverID = ?";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, String> info = new HashMap<>();
                    info.put("fullName", rs.getString("FullName"));
                    info.put("phoneNumber", rs.getString("PhoneNumber"));
                    return info;
                }
                return null;
            }
        }
    }

    /**
     * Lấy AccountID từ DriverID — dùng để ghi AuditLog đúng account. DriverID
     * != AccountID nên phải convert trước khi log.
     */
    public int getAccountIdByDriverId(int driverId) throws Exception {
        String sql = "SELECT AccountID FROM Driver WHERE DriverID = ?";
        try ( java.sql.Connection conn = utils.DbUtils.getConnection();  java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, driverId);
            try ( java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("AccountID") : driverId; // fallback driverId nếu không tìm thấy
            }
        }
    }

    /**
     * Danh sách toàn bộ Driver kèm Account.Status — cho Admin xem để chọn tài
     * khoản khóa/mở khóa.
     */
    public List<Map<String, Object>> getAllDriversForAdmin() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.AccountID, a.FullName, a.Email, a.PhoneNumber, a.Status, "
                + "d.DriverID, d.AvailabilityStatus "
                + "FROM Driver d JOIN Account a ON a.AccountID = d.AccountID "
                + "WHERE d.IsDeleted = 0 ORDER BY d.DriverID";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("accountId", rs.getInt("AccountID"));
                row.put("driverId", rs.getInt("DriverID"));
                row.put("fullName", rs.getString("FullName"));
                row.put("email", rs.getString("Email"));
                row.put("phoneNumber", rs.getString("PhoneNumber"));
                row.put("status", rs.getString("Status"));
                row.put("availabilityStatus", rs.getString("AvailabilityStatus"));
                list.add(row);
            }
        }
        return list;
    }

    /**
     * Danh sách toàn bộ Driver kèm tình trạng (AvailabilityStatus) + số chuyến
     * đã nhận (đếm DriverJobBroadcast đã ACCEPTED) — cho Dispatcher theo dõi.
     */
    public List<Map<String, Object>> getAllDriversForDispatcher() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.AccountID, a.FullName, a.PhoneNumber, a.Status, "
                + "d.DriverID, d.AvailabilityStatus, d.AverageRating, "
                + "(SELECT COUNT(*) FROM DriverJobBroadcast djb "
                + " WHERE djb.AssignedDriverID = d.DriverID AND djb.Status = 'ACCEPTED') AS AcceptedTripCount "
                + "FROM Driver d JOIN Account a ON a.AccountID = d.AccountID "
                + "WHERE d.IsDeleted = 0 ORDER BY d.DriverID";
        try ( Connection conn = DbUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("accountId", rs.getInt("AccountID"));
                row.put("driverId", rs.getInt("DriverID"));
                row.put("fullName", rs.getString("FullName"));
                row.put("phoneNumber", rs.getString("PhoneNumber"));
                row.put("accountStatus", rs.getString("Status"));
                row.put("availabilityStatus", rs.getString("AvailabilityStatus"));
                row.put("averageRating", rs.getBigDecimal("AverageRating"));
                row.put("acceptedTripCount", rs.getInt("AcceptedTripCount"));
                list.add(row);
            }
        }
        return list;
    }

    public void updateAvailabilityStatus(Connection conn, int driverId, String status) throws SQLException {
        try ( PreparedStatement ptm = conn.prepareStatement(UPDATE_DRIVER_AVAILABILITY_BY_DRIVER_ID)) {
            ptm.setString(1, status);
            ptm.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ptm.setInt(3, driverId);
            ptm.executeUpdate();
        }
    }
}
