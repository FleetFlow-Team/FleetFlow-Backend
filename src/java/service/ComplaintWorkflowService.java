package service;

import dao.AccountDAO;
import dao.AuditLogDAO;
import dao.ComplaintActionDAO;
import dao.ComplaintDAO;
import dao.DriverDAO;
import dao.DriverJobBroadcastDAO;
import dao.ExtensionDAO;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow xử lý khiếu nại — rút gọn theo quyết định của PO so với
 * Report_Flow_Complaint.md: chỉ 2 loại LOST_LUGGAGE/OTHER, cả 2 loại đều bắt
 * buộc gắn booking đã hoàn thành (không còn khách vãng lai).
 *
 * Giữ nguyên từ spec gốc: OTHER dùng mô hình 2 lớp (Dispatcher gắn nhãn
 * issueType — 6 loại cố định — rồi mới xử lý bằng 1 trong 4 hành động dùng
 * chung), bộ hành động cố định cho từng loại, nguyên tắc dispatcher KHÔNG gõ
 * text tự do (nội dung khách nhận đều tự sinh), và state machine
 * PENDING -(assign)-> IN_PROGRESS -(resolve)-> RESOLVED / CLOSED_UNRESOLVED.
 *
 * Lỗi nghiệp vụ ném IllegalStateException / IllegalArgumentException với message
 * tiếng Việt — controller map thẳng ra body JSON.
 */
public class ComplaintWorkflowService {

    /** Rule chống lối tắt "gọi hụt 1 cuộc rồi đóng đơn": phải ghi nhận tối thiểu
     *  N lần CONTACT_DRIVER_NO_RESPONSE mới được chốt DRIVER_UNREACHABLE. */
    private static final int MIN_NO_RESPONSE_BEFORE_UNREACHABLE = 3;

    /** 6 issueType cố định cho OTHER (spec mục 3.2). */
    private static final Set<String> VALID_ISSUE_TYPES = new HashSet<>(Arrays.asList(
            "VEHICLE_VIOLATION", "APP_ISSUE", "BILLING_DISPUTE",
            "STAFF_ATTITUDE", "SAFETY_CONCERN", "OTHER_UNCATEGORIZED"));

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
    // Bước 2 (OTHER) — Dispatcher gắn nhãn issueType (6 loại cố định, spec
    // mục 3.2) trước khi xử lý. Đây là bước phân loại, KHÔNG sinh nội dung
    // gửi khách (customer timeline không hiển thị bước này) — chỉ ghi audit.
    // =====================================================================
    public void tag(int complaintId, String issueType, int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        if (!"OTHER".equals(core.get("type"))) {
            throw new IllegalStateException("Gắn nhãn issueType chỉ áp dụng cho đơn OTHER");
        }
        String status = (String) core.get("status");
        if ("RESOLVED".equals(status) || "CLOSED_UNRESOLVED".equals(status)) {
            throw new IllegalStateException("Đơn đã đóng, không thể gắn nhãn lại");
        }
        // P1-2: khoá đổi nhãn sau khi đã có kết luận xử lý — tránh nội dung đã
        // gửi khách (vd ESCALATED trỏ phòng ban theo nhãn cũ) bị sai lệch với
        // nhãn mới mà không thể thu hồi.
        if (actionDAO.countHandleActions(complaintId) > 0) {
            throw new IllegalStateException(
                    "Đơn đã có kết luận xử lý — không thể đổi nhãn issueType nữa");
        }
        if (issueType == null || !VALID_ISSUE_TYPES.contains(issueType.toUpperCase())) {
            throw new IllegalArgumentException("issueType không hợp lệ. Chấp nhận: VEHICLE_VIOLATION, "
                    + "APP_ISSUE, BILLING_DISPUTE, STAFF_ATTITUDE, SAFETY_CONCERN, OTHER_UNCATEGORIZED");
        }
        issueType = issueType.toUpperCase();
        boolean ok = actionDAO.setIssueType(complaintId, issueType);
        if (!ok) {
            throw new IllegalStateException("Không gắn được nhãn — đơn có thể đã đóng hoặc không còn tồn tại");
        }
        audit(dispatcherAccountId, "COMPLAINT_TAG", complaintId, (String) core.get("issueType"), issueType, ip);
    }

    // =====================================================================
    // Bước xử lý OTHER: bộ 4 hành động cố định dùng chung mọi issueType (rule
    // 6: bắt buộc đã gắn nhãn issueType trước khi gọi hành động này).
    //
    // Quyết định nghiệp vụ: 4 hành động kết luận (VERIFIED_HANDLED/CANNOT_VERIFY/REJECTED/ESCALATED) 
    // TỰ ĐÓNG đơn luôn (gộp bước xử lý + đóng đơn). 
    // ESCALATED tự điền {target_department} theo issueType và đóng với ESCALATED_EXTERNAL.
    // =====================================================================
    public String handle(int complaintId, String action, int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        if (!"OTHER".equals(core.get("type"))) {
            throw new IllegalStateException("Hành động xử lý này chỉ áp dụng cho đơn OTHER");
        }
        if (!"IN_PROGRESS".equals(core.get("status"))) {
            throw new IllegalStateException("Phải bấm \"Nhận xử lý\" trước khi thao tác (đơn chưa ở IN_PROGRESS)");
        }
        if (core.get("issueType") == null) {
            throw new IllegalStateException(
                    "Chưa gắn nhãn issueType — phải gắn nhãn (PUT /tag) trước khi thực hiện hành động xử lý");
        }
        // P1-1: mỗi đơn chỉ cho 1 kết luận xử lý — chặn bấm lại/đổi kết luận
        // để tránh tạo nhiều action + spam notify khách cho cùng 1 đơn.
        if (actionDAO.countHandleActions(complaintId) > 0) {
            throw new IllegalStateException(
                    "Đơn đã có kết luận xử lý — không thể xử lý lại");
        }
        if (action == null) {
            throw new IllegalArgumentException(
                    "Thiếu 'action'. Chấp nhận: VERIFIED_HANDLED, CANNOT_VERIFY, ESCALATED, REJECTED");
        }
        action = action.toUpperCase();

        String msg;
        String newStatus;
        String reasonCode = null;
        switch (action) {
            case "VERIFIED_HANDLED":
                msg = "Chúng tôi đã xác minh và xử lý vấn đề bạn phản ánh.";
                newStatus = "RESOLVED";
                break;
            case "CANNOT_VERIFY":
                msg = "Chúng tôi không đủ căn cứ để xác minh vấn đề bạn phản ánh.";
                newStatus = "CLOSED_UNRESOLVED";
                reasonCode = "VIOLATION_NOT_CONFIRMED";
                break;
            case "ESCALATED":
                msg = "Khiếu nại của bạn đã được chuyển đến " + targetDepartment((String) core.get("issueType"))
                        + " để xử lý chuyên sâu. Bộ phận này sẽ liên hệ với bạn qua thông tin liên lạc trong tài khoản. Vui lòng để ý điện thoại và hộp thư trong thời gian tới.";
                newStatus = "RESOLVED";
                reasonCode = "ESCALATED_EXTERNAL";
                break;
            case "REJECTED":
                msg = "Khiếu nại không thuộc phạm vi xử lý hoặc không đủ thông tin hợp lệ.";
                newStatus = "CLOSED_UNRESOLVED";
                reasonCode = "OUT_OF_SCOPE";
                break;
            default:
                throw new IllegalArgumentException(
                        "action không hợp lệ. Chấp nhận: VERIFIED_HANDLED, CANNOT_VERIFY, ESCALATED, REJECTED");
        }

        actionDAO.insertAction(complaintId, dispatcherAccountId, action, reasonCode, msg);

        boolean ok = actionDAO.closeComplaint(complaintId, newStatus, reasonCode, msg);
        if (!ok) {
            throw new IllegalStateException("Đơn vừa bị thao tác bởi người khác, tải lại và thử lại");
        }

        notifyOwner(complaintId, "Cập nhật khiếu nại #" + complaintId, msg);
        audit(dispatcherAccountId, "COMPLAINT_" + action, complaintId, "IN_PROGRESS", newStatus, ip);
        return msg;
    }

    /** {target_department} theo issueType khi ESCALATED (spec mục 6.2). */
    private String targetDepartment(String issueType) {
        if (issueType == null) {
            return "bộ phận liên quan";
        }
        switch (issueType) {
            case "APP_ISSUE":
                return "bộ phận kỹ thuật";
            case "BILLING_DISPUTE":
                return "bộ phận kế toán";
            case "STAFF_ATTITUDE":
                return "bộ phận nhân sự";
            case "SAFETY_CONCERN":
                return "bộ phận an toàn (ưu tiên xử lý)";
            case "VEHICLE_VIOLATION":
            case "OTHER_UNCATEGORIZED":
            default:
                return "bộ phận liên quan";
        }
    }

    // =====================================================================
    // Bước 5 — Chốt đơn: route theo loại. Mỗi loại có hàm riêng, KHÔNG chia
    // sẻ thân hàm, để nhánh LOST_LUGGAGE giữ nguyên tuyệt đối như bản gốc
    // (tránh đụng vào code đã chạy ổn định).
    // =====================================================================
    public String resolve(int complaintId, String outcome, String reasonCode,
            int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        if ("LOST_LUGGAGE".equals(core.get("type"))) {
            return resolveLostLuggage(complaintId, outcome, reasonCode, dispatcherAccountId, ip);
        }
        return resolveOther(complaintId, outcome, reasonCode, dispatcherAccountId, ip);
    }

    // =====================================================================
    // Bước 5 (LOST_LUGGAGE) — outcome = RESOLVED | CLOSED_UNRESOLVED (+ reasonCode).
    // Y nguyên logic gốc, không đổi.
    // =====================================================================
    private String resolveLostLuggage(int complaintId, String outcome, String reasonCode,
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
    // Bước chốt đơn (OTHER) — outcome = RESOLVED | CLOSED_UNRESOLVED (+ reasonCode).
    // Hàm riêng, không dùng chung thân hàm với LOST_LUGGAGE.
    // =====================================================================
    private String resolveOther(int complaintId, String outcome, String reasonCode,
            int dispatcherAccountId, String ip) throws Exception {
        Map<String, Object> core = requireComplaint(complaintId);
        String statusBefore = (String) core.get("status");
        
        if (!"IN_PROGRESS".equals(statusBefore)) {
            throw new IllegalStateException("Chỉ chốt được đơn đang IN_PROGRESS");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Thiếu 'outcome'. Chấp nhận: RESOLVED, CLOSED_UNRESOLVED");
        }
        outcome = outcome.toUpperCase();

        // Rule 5 (tương đương): không được đóng đơn khi chưa ghi nhận hành động xử lý nào
        if (actionDAO.countHandleActions(complaintId) == 0) {
            throw new IllegalStateException(
                    "Chưa ghi nhận hành động xử lý nào — không thể chốt đơn");
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
                case "CUSTOMER_UNREACHABLE":
                    msg = "Chúng tôi không thể liên hệ được với bạn sau nhiều lần thử.";
                    break;
                case "VIOLATION_NOT_CONFIRMED":
                    msg = "Không đủ căn cứ xác nhận vi phạm.";
                    break;
                default:
                    throw new IllegalArgumentException("reason_code không hợp lệ cho OTHER. "
                            + "Chấp nhận: CUSTOMER_UNREACHABLE, VIOLATION_NOT_CONFIRMED");
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
        audit(dispatcherAccountId, "COMPLAINT_CLOSE", complaintId, statusBefore, outcome, ip);
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

    /**
     * Báo cho toàn bộ Admin + Dispatcher đang ACTIVE khi có đơn khiếu nại mới
     * (bổ sung theo quyết định của PO, không có trong Report_Flow_Complaint.md
     * gốc — spec gốc chỉ để Dispatcher tự vào danh sách xem, không cảnh báo).
     */
    public void notifyStaffNewComplaint(int complaintId, String type) {
        try {
            String title = "Khiếu nại mới #" + complaintId;
            String message = "Có khiếu nại mới loại "
                    + ("LOST_LUGGAGE".equals(type) ? "Thất lạc hành lý" : "Khác")
                    + " cần tiếp nhận xử lý.";
            AccountDAO accountDAO = new AccountDAO();
            for (int accId : accountDAO.getActiveDispatcherAccountIds()) {
                extensionDAO.createNotification(accId, null, title, message, "COMPLAINT_NEW", "IN_APP");
            }
            for (int accId : accountDAO.getActiveAdminAccountIds()) {
                extensionDAO.createNotification(accId, null, title, message, "COMPLAINT_NEW", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace(); // notify lỗi không được chặn nghiệp vụ chính
        }
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