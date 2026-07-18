package service;

import dao.AuditLogDAO;
import dao.ComplaintActionDAO;
import dao.ComplaintDAO;
import dao.DriverDAO;
import dao.DriverJobBroadcastDAO;
import dao.ExtensionDAO;
import java.util.Map;

/**
 * Workflow xử lý khiếu nại LOST_LUGGAGE theo spec Report_Flow_Complaint.md.
 *
 * Nguyên tắc xuyên suốt: dispatcher KHÔNG gõ text tự do — mọi thao tác là chọn
 * hành động cố định, nội dung khách nhìn thấy do hệ thống tự sinh (spec mục 6).
 *
 * State machine: PENDING -(assign)-> IN_PROGRESS -(resolve)-> RESOLVED / CLOSED_UNRESOLVED
 *
 * Lỗi nghiệp vụ ném IllegalStateException / IllegalArgumentException với message
 * tiếng Việt — controller map thẳng ra body JSON.
 *
 * PHẠM VI: LOST_LUGGAGE. Loại OTHER (mô hình 2 lớp issueType + 4 action chung)
 * chưa triển khai — resolve cho OTHER tạm đi đường legacy để không vỡ chức năng cũ.
 */
public class ComplaintWorkflowService {

    /** Rule chống lối tắt "gọi hụt 1 cuộc rồi đóng đơn": phải ghi nhận tối thiểu
     *  N lần CONTACT_DRIVER_NO_RESPONSE mới được chốt DRIVER_UNREACHABLE. */
    private static final int MIN_NO_RESPONSE_BEFORE_UNREACHABLE = 3;

    private final ComplaintActionDAO actionDAO = new ComplaintActionDAO();
    private final ComplaintDAO complaintDAO = new ComplaintDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    // =====================================================================
    // Bước 2 — Dispatcher bấm "Nhận xử lý": PENDING -> IN_PROGRESS
    // =====================================================================
    public void assign(int complaintId, int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        String status = (String) core.get("status");
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException(
                    "Đơn không ở trạng thái PENDING (hiện tại: " + status + ") — có thể đã có người nhận xử lý");
        }
        boolean ok = actionDAO.assignComplaint(complaintId, dispatcherAccountId);
        if (!ok) {
            // race: dispatcher khác vừa nhận trước vài trăm ms
            throw new IllegalStateException("Đơn vừa được dispatcher khác nhận xử lý");
        }
        String msg = "Đơn đang được xử lý";
        actionDAO.insertAction(complaintId, dispatcherAccountId, "ASSIGN", null, msg);
        notifyOwner(complaintId, "Khiếu nại #" + complaintId + " đang được xử lý", msg);
        audit(dispatcherAccountId, "COMPLAINT_ASSIGN", complaintId, "PENDING", "IN_PROGRESS", ip);
    }

    // =====================================================================
    // Bước 3 — Ghi kết quả liên hệ tài xế (chỉ LOST_LUGGAGE):
    // HAS_ITEM / NO_ITEM / NO_RESPONSE. Trả về message tự sinh để FE hiển thị.
    // =====================================================================
    public String recordContactDriver(int complaintId, String result,
            int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        if (!"LOST_LUGGAGE".equals(core.get("type"))) {
            throw new IllegalStateException("Hành động liên hệ tài xế chỉ áp dụng cho đơn LOST_LUGGAGE");
        }
        if (!"IN_PROGRESS".equals(core.get("status"))) {
            throw new IllegalStateException("Phải bấm \"Nhận xử lý\" trước khi thao tác (đơn chưa ở IN_PROGRESS)");
        }
        if (result == null) {
            throw new IllegalArgumentException("Thiếu 'result'. Chấp nhận: HAS_ITEM, NO_ITEM, NO_RESPONSE");
        }
        result = result.toUpperCase();

        String actionCode;
        String msg;
        switch (result) {
            case "HAS_ITEM":
                // Spec bước 3->4: hệ thống TỰ lấy SĐT tài xế từ hồ sơ, dispatcher
                // không nhập tay; hẹn nhận đồ do khách + tài xế tự thoả thuận.
                String driverPhone = lookupDriverPhone(core);
                actionCode = "CONTACT_DRIVER_HAS_ITEM";
                msg = "Tài xế xác nhận đang giữ đồ của bạn. Vui lòng liên hệ SĐT "
                        + driverPhone + " để sắp xếp nhận lại.";
                break;
            case "NO_ITEM":
                actionCode = "CONTACT_DRIVER_NO_ITEM";
                msg = "Tài xế xác nhận không có đồ thất lạc của bạn.";
                break;
            case "NO_RESPONSE":
                actionCode = "CONTACT_DRIVER_NO_RESPONSE";
                msg = "Chưa liên hệ được tài xế, hệ thống sẽ tiếp tục thử lại.";
                break;
            default:
                throw new IllegalArgumentException("result không hợp lệ. Chấp nhận: HAS_ITEM, NO_ITEM, NO_RESPONSE");
        }

        actionDAO.insertAction(complaintId, dispatcherAccountId, actionCode, null, msg);
        notifyOwner(complaintId, "Cập nhật khiếu nại #" + complaintId, msg);
        audit(dispatcherAccountId, "COMPLAINT_" + actionCode, complaintId, null, result, ip);
        return msg;
    }

    // =====================================================================
    // Bước 5 — Chốt đơn: outcome = RESOLVED | CLOSED_UNRESOLVED (+ reasonCode)
    // =====================================================================
    public String resolve(int complaintId, String outcome, String reasonCode,
            int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        if (!"IN_PROGRESS".equals(core.get("status"))) {
            throw new IllegalStateException("Chỉ chốt được đơn đang IN_PROGRESS");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Thiếu 'outcome'. Chấp nhận: RESOLVED, CLOSED_UNRESOLVED");
        }
        outcome = outcome.toUpperCase();

        // Rule 5: không được đóng đơn khi chưa ghi nhận action nào
        if (actionDAO.countActionsByPrefix(complaintId, "CONTACT_DRIVER_") == 0) {
            throw new IllegalStateException(
                    "Chưa ghi nhận kết quả liên hệ tài xế nào — không thể chốt đơn");
        }

        String msg;
        if ("RESOLVED".equals(outcome)) {
            msg = "Đơn khiếu nại đã được xử lý xong. Cảm ơn bạn đã phản hồi.";
            reasonCode = null;
        } else if ("CLOSED_UNRESOLVED".equals(outcome)) {
            // Rule 8: CLOSED_UNRESOLVED bắt buộc có reason_code
            if (reasonCode == null) {
                throw new IllegalArgumentException(
                        "Đóng đơn không thành công thì bắt buộc phải chọn lý do");
            }
            reasonCode = reasonCode.toUpperCase();
            switch (reasonCode) {
                case "NO_ITEM_FOUND":
                    msg = "Rất tiếc, tài xế xác nhận không có đồ thất lạc của bạn.";
                    break;
                case "CUSTOMER_UNREACHABLE":
                    msg = "Chúng tôi không thể liên hệ được với bạn sau nhiều lần thử.";
                    break;
                case "DRIVER_UNREACHABLE":
                    // Chống lối tắt: NO_RESPONSE 1 lần chưa đủ căn cứ "không liên hệ được"
                    int tries = actionDAO.countActionsByCode(complaintId, "CONTACT_DRIVER_NO_RESPONSE");
                    if (tries < MIN_NO_RESPONSE_BEFORE_UNREACHABLE) {
                        throw new IllegalStateException("Cần tối thiểu " + MIN_NO_RESPONSE_BEFORE_UNREACHABLE
                                + " lần ghi nhận NO_RESPONSE trước khi chốt DRIVER_UNREACHABLE (hiện có "
                                + tries + ")");
                    }
                    msg = "Không thể liên hệ được với tài xế để xác minh.";
                    break;
                default:
                    throw new IllegalArgumentException("reason_code không hợp lệ cho LOST_LUGGAGE. "
                            + "Chấp nhận: NO_ITEM_FOUND, CUSTOMER_UNREACHABLE, DRIVER_UNREACHABLE");
            }
        } else {
            throw new IllegalArgumentException("outcome không hợp lệ. Chấp nhận: RESOLVED, CLOSED_UNRESOLVED");
        }

        boolean ok = actionDAO.closeComplaint(complaintId, outcome, reasonCode, msg);
        if (!ok) {
            throw new IllegalStateException("Đơn vừa bị thao tác bởi người khác, tải lại và thử lại");
        }
        actionDAO.insertAction(complaintId, dispatcherAccountId,
                "RESOLVED".equals(outcome) ? "RESOLVE" : "CLOSE_UNRESOLVED", reasonCode, msg);
        notifyOwner(complaintId, "Khiếu nại #" + complaintId + " đã đóng", msg);
        audit(dispatcherAccountId, "COMPLAINT_CLOSE", complaintId, "IN_PROGRESS", outcome, ip);
        return msg;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Map<String, Object> requireComplaint(int complaintId) throws Exception {
        Map<String, Object> core = actionDAO.getComplaintCore(complaintId);
        if (core == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn khiếu nại #" + complaintId);
        }
        return core;
    }

    /** BookingID -> driver đã ACCEPTED (nguồn sự thật DriverJobBroadcast) -> SĐT. */
    private String lookupDriverPhone(Map<String, Object> core) throws Exception {
        Integer bookingId = (Integer) core.get("bookingId");
        if (bookingId == null) {
            throw new IllegalStateException("Đơn LOST_LUGGAGE không gắn BookingID — không xác định được tài xế");
        }
        int driverId = new DriverJobBroadcastDAO().getAcceptedDriverId(bookingId);
        if (driverId == -1) {
            throw new IllegalStateException("Booking #" + bookingId + " chưa có tài xế ACCEPTED — không có SĐT để cung cấp");
        }
        Map<String, String> info = new DriverDAO().getDriverNameAndPhone(driverId);
        if (info == null || info.get("phoneNumber") == null || info.get("phoneNumber").isEmpty()) {
            throw new IllegalStateException("Không tìm thấy SĐT của tài xế #" + driverId);
        }
        return info.get("phoneNumber");
    }

    /** Notify chủ đơn qua in-app. Đơn của guest (không CustomerID) thì bỏ qua —
     *  kênh SMS/email cho guest là câu hỏi mở mục 11.2, chưa chốt. */
    private void notifyOwner(int complaintId, String title, String message) {
        try {
            int[] info = complaintDAO.getComplaintCustomerInfo(complaintId);
            int accountId = info[0];
            Integer bookingId = info[1] == -1 ? null : info[1];
            if (accountId != -1) {
                extensionDAO.createNotification(accountId, bookingId, title, message,
                        "COMPLAINT_UPDATE", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace(); // notify lỗi không được chặn nghiệp vụ chính
        }
    }

    private void audit(int accountId, String action, int complaintId,
            String oldValue, String newValue, String ip) {
        try {
            auditLogDAO.log(accountId, action, "Complaint", String.valueOf(complaintId),
                    oldValue, newValue, ip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}