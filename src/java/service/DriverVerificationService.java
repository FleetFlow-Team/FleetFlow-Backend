package service;

import dao.DriverVerificationDAO;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.Account;
import model.IdentityDocument;

public class DriverVerificationService {

    private final DriverVerificationDAO dao = new DriverVerificationDAO();

    // BE-13: Lấy danh sách pending driver kèm giấy tờ
    // Trả về List, mỗi phần tử là 1 String JSON thủ công (giống pattern BookingController)
    public List<Account> getPendingDrivers() throws SQLException, ClassNotFoundException {
        return dao.getPendingDriverAccounts();
    }

    public List<IdentityDocument> getDocsByAccountId(int accountId) throws SQLException, ClassNotFoundException {
        return dao.getDocsByAccountId(accountId);
    }

    // BE-14: Duyệt hồ sơ
    public void approveDriver(int accountId, int adminAccountId) throws Exception {
        if (accountId <= 0) {
            throw new IllegalArgumentException("AccountID không hợp lệ");
        }
        boolean ok = dao.approveDriver(accountId, adminAccountId);
        if (!ok) {
            throw new IllegalArgumentException("Driver không tồn tại hoặc không ở trạng thái PENDING_APPROVAL");
        }
    }

    // BE-15: Từ chối hồ sơ
    public void rejectDriver(int accountId, String rejectReason) throws Exception {
        boolean ok = dao.rejectDriver(accountId, rejectReason);

        if (!ok) {
            throw new IllegalArgumentException(
                    "Driver không tồn tại hoặc không ở trạng thái PENDING");
        }
    }
}
