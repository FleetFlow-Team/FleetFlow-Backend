package service;

import dao.CustomerLockDAO;
import dao.ExtensionDAO;
import java.math.BigDecimal;
import java.util.List;

/**
 * Quy tắc đã chốt: - Nợ >= 1,000,000đ → tự động ghi nhận + thông báo cho
 * Customer VÀ Admin (KHÔNG tự khóa) - Admin xem cảnh báo, tự bấm khóa tay
 * (lockAccount) - Khi khóa → thông báo cho Customer, Account.Status = LOCKED →
 * chặn tạo booking mới - Mở khóa → chỉ cho phép khi nợ đã về dưới ngưỡng (đã
 * thanh toán xong)
 */
public class CustomerLockService {

    private final CustomerLockDAO lockDAO = new CustomerLockDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();

    /**
     * Gọi sau mỗi lần ghi CustomerWalletLedger (ví dụ sau khi tính phạt hủy
     * booking) để kiểm tra và gửi cảnh báo nếu nợ vượt ngưỡng. KHÔNG tự khóa
     * tài khoản.
     *
     * Admin nào nhận cảnh báo: tạm gửi cho accountId=1 (admin đầu tiên) — nên
     * thay bằng truy vấn danh sách Admin thật nếu cần gửi cho nhiều admin.
     */
    public void checkAndWarnIfDebtExceeded(int customerId, int adminAccountIdToNotify) throws Exception {
        BigDecimal debt = lockDAO.getCurrentDebt(customerId);

        // debt âm nghĩa là đang nợ; so sánh độ lớn với ngưỡng
        if (debt.compareTo(CustomerLockDAO.DEBT_WARNING_THRESHOLD.negate()) <= 0) {
            int accountId = lockDAO.getAccountIdByCustomerId(customerId);
            String debtAbsStr = debt.abs().toPlainString();

            // Thông báo cho Customer
            extensionDAO.createNotification(
                    accountId, null,
                    "Cảnh báo công nợ",
                    "Tài khoản của bạn đang nợ " + debtAbsStr + "đ. "
                    + "Vui lòng thanh toán để tránh bị tạm khóa tài khoản.",
                    "DEBT_WARNING", "IN_APP"
            );

            // Thông báo cho Admin
            extensionDAO.createNotification(
                    adminAccountIdToNotify, null,
                    "Khách hàng vượt ngưỡng nợ",
                    "Customer #" + customerId + " (AccountID " + accountId + ") đang nợ "
                    + debtAbsStr + "đ, vượt ngưỡng cảnh báo "
                    + CustomerLockDAO.DEBT_WARNING_THRESHOLD.toPlainString() + "đ.",
                    "DEBT_WARNING", "IN_APP"
            );
        }
    }

    /**
     * Admin khóa tài khoản customer — chủ động bấm, không điều kiện ràng buộc
     * nợ (Admin có thể khóa vì lý do khác ngoài nợ).
     */
    public void lockCustomerAccount(int customerId) throws Exception {
        int accountId = lockDAO.getAccountIdByCustomerId(customerId);
        lockDAO.lockAccount(accountId);

        BigDecimal debt = lockDAO.getCurrentDebt(customerId);
        String debtInfo = debt.compareTo(BigDecimal.ZERO) < 0
                ? " Công nợ hiện tại: " + debt.abs().toPlainString() + "đ."
                : "";

        extensionDAO.createNotification(
                accountId, null,
                "Tài khoản đã bị tạm khóa",
                "Tài khoản của bạn đã bị tạm khóa và không thể đặt xe mới."
                + debtInfo + " Vui lòng liên hệ để được hỗ trợ mở lại tài khoản.",
                "ACCOUNT_LOCKED", "BOTH"
        );
    }

    /**
     * Admin mở khóa tài khoản — chỉ cho phép khi nợ đã về dưới ngưỡng cảnh báo
     * (tức là khách đã thanh toán đủ).
     */
    public void unlockCustomerAccount(int customerId) throws Exception {
        BigDecimal debt = lockDAO.getCurrentDebt(customerId);

        if (debt.compareTo(CustomerLockDAO.DEBT_WARNING_THRESHOLD.negate()) <= 0) {
            throw new IllegalArgumentException(
                    "Không thể mở khóa: khách vẫn còn nợ " + debt.abs().toPlainString()
                    + "đ, vượt ngưỡng " + CustomerLockDAO.DEBT_WARNING_THRESHOLD.toPlainString() + "đ. "
                    + "Yêu cầu khách thanh toán hết nợ trước khi mở khóa."
            );
        }

        int accountId = lockDAO.getAccountIdByCustomerId(customerId);
        lockDAO.unlockAccount(accountId);

        extensionDAO.createNotification(
                accountId, null,
                "Tài khoản đã được mở khóa",
                "Tài khoản của bạn đã được mở khóa. Bạn có thể tiếp tục đặt xe.",
                "ACCOUNT_UNLOCKED", "BOTH"
        );
    }

    /**
     * Dùng ở BookingController.doPost — chặn tạo booking mới nếu account đang
     * LOCKED.
     */
    public void requireAccountNotLocked(int accountId) throws Exception {
        if (lockDAO.isAccountLocked(accountId)) {
            throw new IllegalArgumentException(
                    "Tài khoản của bạn đang bị tạm khóa do công nợ chưa thanh toán. "
                    + "Không thể đặt xe mới cho tới khi được mở khóa."
            );
        }
    }

    /**
     * Overload nhận trực tiếp customerId (thường gặp hơn ở các API
     * Customer-facing như tạo booking) — tự resolve sang accountId rồi check
     * lock.
     */
    public void requireCustomerNotLocked(int customerId) throws Exception {
        int accountId = lockDAO.getAccountIdByCustomerId(customerId);
        requireAccountNotLocked(accountId);
    }

    public BigDecimal getCurrentDebt(int customerId) throws Exception {

        CustomerLockDAO dao = new CustomerLockDAO();

        return dao.getCurrentDebt(customerId);
    }
    public List<CustomerLockDAO.CustomerInfo> getAllCustomers()
        throws Exception {

    CustomerLockDAO dao = new CustomerLockDAO();

    return dao.getAllCustomers();
}
}
