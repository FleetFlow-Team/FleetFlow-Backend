package service;

import controller.DriverDispatchController;
import dao.AuditLogDAO;
import dao.BookingDAO;
import dao.DriverJobBroadcastDAO;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import model.Booking;
import model.BookingDetail;
import model.DriverJobBroadcast;
import utils.DbUtils;

/**
 * Service điều phối toàn bộ luồng sau khi Customer tạo Booking:
 *
 * PENDING (Customer tạo xong)
 * → Dispatcher CONFIRM (hoặc auto sau timeout) → APPROVED
 * → Hệ thống tự AUTO-DISPATCH driver rảnh lâu nhất → DISPATCHED
 * → Dispatcher REJECT → REJECTED
 *
 * DISPATCHED
 * → Driver ACCEPT → DriverJobBroadcast.Status=ACCEPTED → Booking.Status =
 * CONFIRMED
 * → Driver REJECT → DriverJobBroadcast.Status=REJECTED → tự động dispatch
 * driver tiếp theo
 * → Nếu hết driver → Booking.Status = UNASSIGNED (alert dispatcher)
 *
 * Mọi hành động đều ghi vào AuditLog.
 */
public class BookingWorkflowService {

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverJobBroadcastDAO broadcastDAO = new DriverJobBroadcastDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    // ===================== BƯỚC 1: Dispatcher confirm booking
    // =====================

    /**
     * Dispatcher confirm booking — PENDING → APPROVED → tự động dispatch driver.
     * Đây là bước duy nhất dispatcher cần làm trong luồng bình thường.
     */
    public void approveBooking(int bookingId, Integer dispatcherAccountId, String ipAddress) throws Exception {
        Booking booking = requireBookingInStatus(bookingId, "PENDING",
                "Chỉ confirm được booking đang ở trạng thái PENDING");

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "APPROVED");
                auditLogDAO.log(conn, dispatcherAccountId, "APPROVE_BOOKING", "Booking",
                        String.valueOf(bookingId), booking.getStatus(), "APPROVED", ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Gửi Notification cho Customer
        try {
            Integer custAccountId = new dao.CustomerDAO().getAccountIdByCustomerId(booking.getCustomerId());
            if (custAccountId != null) {
                new dao.ExtensionDAO().createNotification(custAccountId, bookingId,
                        "Đơn đặt xe đã được duyệt",
                        "Cuốc xe #" + bookingId
                                + " của bạn đã được quản trị viên duyệt và đang trong quá trình tìm tài xế.",
                        "BOOKING_APPROVED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Sau khi approve xong, tự động dispatch driver — ngoài transaction approve
        autoDispatchNextDriver(bookingId, new ArrayList<>(), dispatcherAccountId, ipAddress);
    }

    /**
     * Dispatcher từ chối booking — PENDING → REJECTED.
     */
    public void rejectBooking(int bookingId, int dispatcherAccountId, String reason, String ipAddress)
            throws Exception {
        // Cho phép reject cả PENDING (chưa dispatch) và UNASSIGNED (hết driver)
        Booking booking = bookingDAO.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Không tìm thấy booking #" + bookingId);
        }
        if (!"PENDING".equalsIgnoreCase(booking.getStatus())
                && !"UNASSIGNED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException(
                "Chỉ từ chối được booking ở trạng thái PENDING hoặc UNASSIGNED. "
                + "Trạng thái hiện tại: " + booking.getStatus());
        }

        boolean wasUnassigned = "UNASSIGNED".equalsIgnoreCase(booking.getStatus());
        java.math.BigDecimal refunded = java.math.BigDecimal.ZERO;
        dao.ExtensionDAO extDAO = new dao.ExtensionDAO();

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "REJECTED");
                auditLogDAO.log(conn, dispatcherAccountId, "REJECT_BOOKING", "Booking",
                        String.valueOf(bookingId), booking.getStatus(),
                        "REJECTED" + (reason != null ? " (" + reason + ")" : ""), ipAddress);

                // Không tìm được tài xế — lỗi không thuộc về khách, hoàn cọc nếu đã đóng
                if (wasUnassigned) {
                    // refunded = extDAO.refundDeposit(...) — code cũ, đã chuyển sang PaymentService
                    refunded = new PaymentService().refundDeposit(conn, bookingId, booking.getCustomerId(), reason);
                    if (refunded.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        auditLogDAO.log(conn, dispatcherAccountId, "REFUND_DEPOSIT", "Booking",
                                String.valueOf(bookingId), "DEPOSIT_COMPLETED",
                                "REFUNDED " + refunded.toPlainString() + "đ (UNASSIGNED)", ipAddress);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Notify customer khi dispatcher hủy booking UNASSIGNED
        if (wasUnassigned) {
            try {
                int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
                if (customerAccountId != -1) {
                    String refundMsg = refunded.compareTo(java.math.BigDecimal.ZERO) > 0
                            ? " Cọc " + refunded.toPlainString() + "đ đã được hoàn lại vào ví của bạn."
                            : "";
                    extDAO.createNotification(customerAccountId, bookingId,
                            "Booking #" + bookingId + " đã bị hủy",
                            "Rất tiếc, chúng tôi không thể tìm được tài xế cho chuyến của bạn"
                                    + (reason != null && !reason.isEmpty() ? ". Lý do: " + reason : ".")
                                    + refundMsg
                                    + " Vui lòng đặt lại hoặc liên hệ hỗ trợ.",
                            "BOOKING_CANCELLED", "IN_APP");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ===================== BƯỚC 2: Auto-dispatch driver =====================

    /**
     * Tự động tìm driver rảnh lâu nhất và dispatch.
     * excludeDriverIds: danh sách driver đã từ chối trước đó — bỏ qua họ.
     *
     * Nếu không còn driver → chuyển booking sang UNASSIGNED để alert dispatcher.
     */
    public void autoDispatchNextDriver(int bookingId, List<Integer> excludeDriverIds,
            Integer triggeredByAccountId, String ipAddress) throws Exception {

        Booking booking = bookingDAO.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Không tìm thấy booking #" + bookingId);
        }

        int nextDriverId = bookingDAO.findNextAvailableDriver(booking.getVehicleId(), excludeDriverIds);

        if (nextDriverId == -1) {
            // Hết driver — chuyển sang UNASSIGNED để dispatcher xử lý
            try (Connection conn = DbUtils.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    bookingDAO.updateStatus(conn, bookingId, "UNASSIGNED");
                    auditLogDAO.log(conn, triggeredByAccountId, "AUTO_DISPATCH_FAILED", "Booking",
                            String.valueOf(bookingId), booking.getStatus(),
                            "UNASSIGNED — hết driver available", ipAddress);
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
            // Notify customer: đang tìm tài xế
            try {
                dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
                int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
                if (customerAccountId != -1) {
                    extDAO.createNotification(customerAccountId, bookingId,
                            "Đang tìm tài xế cho bạn",
                            "Booking #" + bookingId + " hiện chưa có tài xế phù hợp. "
                                    + "Chúng tôi đang tiếp tục tìm kiếm, vui lòng chờ.",
                            "BOOKING_UNASSIGNED", "IN_APP");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        // Dispatch driver tìm được
        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                broadcastDAO.dispatchDriver(conn, bookingId, nextDriverId, triggeredByAccountId);
                bookingDAO.updateStatus(conn, bookingId, "DISPATCHED");
                auditLogDAO.log(conn, triggeredByAccountId, "AUTO_DISPATCH_DRIVER", "Booking",
                        String.valueOf(bookingId), booking.getStatus(),
                        "DISPATCHED (auto → driverId=" + nextDriverId + ")", ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Gửi Notification cho Customer
        try {
            Integer custAccountId = new dao.CustomerDAO().getAccountIdByCustomerId(booking.getCustomerId());
            if (custAccountId != null) {
                java.util.Map<String, String> driverInfo = new dao.DriverDAO().getDriverNameAndPhone(nextDriverId);
                String dName = driverInfo != null ? driverInfo.get("fullName") : ("#" + nextDriverId);
                new dao.ExtensionDAO().createNotification(custAccountId, bookingId,
                        "Đã tìm thấy tài xế",
                        "Tài xế " + dName + " đã được gán cho cuốc xe #" + bookingId + " của bạn.",
                        "DRIVER_ASSIGNED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cập nhật LastAssignedAt để lần sau sort đúng
        bookingDAO.updateDriverLastAssigned(nextDriverId);

        // Gửi notification cho driver vừa được gán
        try {
            BookingDetail detail = bookingDAO.findDetailByBookingId(bookingId);
            Booking b = bookingDAO.findById(bookingId);
            String pickup = detail != null ? detail.getPickupAddress() : null;
            String customerName = b != null ? b.getCustomerName() : null;
            String depTime = (detail != null && detail.getDepartureTime() != null)
                    ? detail.getDepartureTime().toString()
                    : null;
            broadcastDAO.createDriverNotification(nextDriverId, bookingId, pickup, customerName, depTime);
        } catch (Exception e) {
            // Notification thất bại không nên làm hỏng luồng chính
            e.printStackTrace();
        }

        // Gửi notification cho TẤT CẢ Dispatcher đang active — để biết booking
        // vừa được gán cho driver nào (hiện tại dispatcher không biết do auto-dispatch)
        try {
            notifyDispatchersDriverAssigned(bookingId, nextDriverId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Thông báo cho toàn bộ Dispatcher đang active khi 1 booking vừa được
     * tự động gán cho driver nào — vì auto-dispatch chạy ngầm, dispatcher
     * không biết booking #X đang được giao cho ai nếu không có cái này.
     */
    private void notifyDispatchersDriverAssigned(int bookingId, int driverId) throws Exception {
        java.util.List<Integer> dispatcherAccountIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
        if (dispatcherAccountIds.isEmpty())
            return;

        java.util.Map<String, String> driverInfo = new dao.DriverDAO().getDriverNameAndPhone(driverId);
        String driverName = driverInfo != null ? driverInfo.get("fullName") : ("Driver #" + driverId);

        String title = "Booking #" + bookingId + " đã được gán tài xế";
        String message = "Hệ thống đã tự động gán booking #" + bookingId
                + " cho tài xế " + driverName + " (DriverID=" + driverId + ").";

        dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
        for (int dispatcherAccountId : dispatcherAccountIds) {
            extDAO.createNotification(dispatcherAccountId, bookingId, title, message,
                    "BOOKING_DRIVER_ASSIGNED", "IN_APP");
        }
    }

    /**
     * Dispatcher dispatch thủ công — vẫn giữ cho trường hợp UNASSIGNED cần can
     * thiệp tay.
     */
    public long dispatchDriver(int bookingId, int driverId, int dispatcherAccountId, String ipAddress)
            throws Exception {
        Booking booking = bookingDAO.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Không tìm thấy booking #" + bookingId);
        }
        if (!"APPROVED".equalsIgnoreCase(booking.getStatus())
                && !"UNASSIGNED".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException(
                    "Chỉ dispatch thủ công được khi booking ở trạng thái APPROVED hoặc UNASSIGNED. "
                            + "Trạng thái hiện tại: " + booking.getStatus());
        }

        if (broadcastDAO.hasPendingBroadcast(bookingId)) {
            throw new IllegalArgumentException(
                    "Booking này đang có lệnh dispatch khác chờ phản hồi, không thể dispatch thêm.");
        }

        // Không cho gán chuyến cho driver có account đang bị Admin khóa
        int driverAccountId = new dao.DriverDAO().getAccountIdByDriverId(driverId);
        if (driverAccountId == -1) {
            throw new IllegalArgumentException("Không tìm thấy driver #" + driverId);
        }
        if (new dao.CustomerLockDAO().isAccountLocked(driverAccountId)) {
            throw new IllegalArgumentException(
                    "Driver #" + driverId + " đang bị khóa tài khoản, không thể gán chuyến.");
        }

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long broadcastId = broadcastDAO.dispatchDriver(conn, bookingId, driverId, dispatcherAccountId);
                bookingDAO.updateStatus(conn, bookingId, "DISPATCHED");
                auditLogDAO.log(conn, dispatcherAccountId, "DISPATCH_DRIVER", "Booking",
                        String.valueOf(bookingId), booking.getStatus(),
                        "DISPATCHED (manual → driverId=" + driverId + ")", ipAddress);
                conn.commit();
                bookingDAO.updateDriverLastAssigned(driverId);

                // Notify customer + driver khi dispatcher gán thủ công
                try {
                    dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
                    java.util.Map<String, String> driverInfo = new dao.DriverDAO().getDriverNameAndPhone(driverId);
                    String driverName = driverInfo != null ? driverInfo.get("fullName") : ("Driver #" + driverId);

                    int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
                    if (customerAccountId != -1) {
                        extDAO.createNotification(customerAccountId, bookingId,
                                "Đã tìm được tài xế cho bạn",
                                "Booking #" + bookingId + " đã được gán cho tài xế "
                                        + driverName + ". Vui lòng chờ tài xế xác nhận.",
                                "BOOKING_DRIVER_ASSIGNED", "IN_APP");
                    }

                    if (driverAccountId != -1) {
                        extDAO.createNotification(driverAccountId, bookingId,
                                "Bạn được gán chuyến mới",
                                "Dispatcher đã gán booking #" + bookingId + " cho bạn. "
                                        + "Vui lòng xác nhận nhận chuyến.",
                                "DISPATCH_ASSIGNED", "IN_APP");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return broadcastId;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ===================== BƯỚC 3: Driver phản hồi lệnh dispatch
    // =====================

    /**
     * Driver accept lệnh dispatch → Booking chuyển CONFIRMED.
     */
    public void driverAccept(int broadcastId, int driverId, String ipAddress) throws Exception {
        int updated = broadcastDAO.respondToDispatch(broadcastId, driverId, "ACCEPTED");
        if (updated == 0) {
            throw new IllegalArgumentException(buildDispatchFailureMessage(broadcastId));
        }

        int bookingId = getBookingIdFromBroadcast(broadcastId);
        int driverAccountId = new dao.DriverDAO().getAccountIdByDriverId(driverId);
        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "CONFIRMED");
                auditLogDAO.log(conn, driverAccountId, "DRIVER_ACCEPT", "Booking",
                        String.valueOf(bookingId), "DISPATCHED", "CONFIRMED", ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Notify dispatcher: driver đã accept, booking đã CONFIRMED
        try {
            notifyDispatchersDriverResponded(bookingId, driverId, true, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Tự động tạo (hoặc tái sử dụng) Payment DEPOSIT 30% sau khi booking CONFIRMED
        // FE sẽ dùng thông tin này để redirect khách sang VNPay
        //
        // ---- Code cũ của teammate (comment lại để đối chiếu, không xóa) ----
        // dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
        // if (!extDAO.hasExistingDeposit(bookingId)) {
        //     java.math.BigDecimal estimatedTotal = extDAO.calculateFinalPayment(bookingId);
        //     if (estimatedTotal != null && estimatedTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
        //         java.math.BigDecimal deposit = estimatedTotal
        //                 .multiply(new java.math.BigDecimal("0.30"))
        //                 .setScale(0, java.math.RoundingMode.HALF_UP);
        //         boolean created = extDAO.createPendingPayment(bookingId, "DEPOSIT", "VNPAY", deposit.doubleValue());
        //         if (created) {
        //             int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
        //             if (customerAccountId != -1) {
        //                 extDAO.createNotification(customerAccountId, bookingId, ...);
        //             }
        //         }
        //     }
        // }
        // ---------------------------------------------------------------------
        try {
            dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
            PaymentService paymentService = new PaymentService();
            // Guard giữ từ teammate: nếu driverAccept chạy lại lần 2 cho cùng booking
            // (double-click, retry...) thì bỏ qua toàn bộ khối tạo cọc + notify —
            // getOrCreatePending tự nó đã chống tạo Payment trùng, nhưng guard này
            // thêm lớp chống gửi thông báo "vui lòng thanh toán cọc" trùng lặp.
            if (!extDAO.hasExistingDeposit(bookingId)) {
                java.math.BigDecimal deposit = paymentService.depositAmountOf(bookingId);
                if (deposit.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    paymentService.getOrCreatePending(bookingId, "DEPOSIT", "VNPAY", deposit);
                    int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
                    if (customerAccountId != -1) {
                        extDAO.createNotification(customerAccountId, bookingId,
                                "Vui lòng thanh toán cọc 30%",
                                "Chuyến đi #" + bookingId + " đã có tài xế. "
                                        + "Vui lòng thanh toán cọc " + deposit.toPlainString()
                                        + "đ để xác nhận chuyến.",
                                "PAYMENT_DEPOSIT_REQUIRED", "IN_APP");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Driver reject lệnh dispatch → Hệ thống tự tìm driver tiếp theo.
     * Nếu hết driver → UNASSIGNED để alert dispatcher.
     */
    public void driverReject(int broadcastId, int driverId, String reason, String ipAddress) throws Exception {
        // Truyền reason để lưu vào cột RejectReason — trước đây bị thiếu nên reason
        // chỉ nằm trong AuditLog chứ không lưu vào DriverJobBroadcast
        int updated = broadcastDAO.respondToDispatch(broadcastId, driverId, "REJECTED", reason);
        if (updated == 0) {
            throw new IllegalArgumentException(buildDispatchFailureMessage(broadcastId));
        }

        int bookingId = getBookingIdFromBroadcast(broadcastId);
        int driverAccountId = new dao.DriverDAO().getAccountIdByDriverId(driverId);

        // Ghi audit trước
        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "APPROVED"); // reset để dispatch tiếp
                auditLogDAO.log(conn, driverAccountId, "DRIVER_REJECT", "Booking",
                        String.valueOf(bookingId), "DISPATCHED",
                        "APPROVED — driver từ chối" + (reason != null ? ": " + reason : ""), ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Notify dispatcher: driver đã từ chối, kèm lý do
        try {
            notifyDispatchersDriverResponded(bookingId, driverId, false, reason);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cập nhật LastAssignedAt của driver vừa reject (đẩy xuống cuối hàng)
        bookingDAO.updateDriverLastAssigned(driverId);

        // Thu thập tất cả driver đã reject booking này để bỏ qua
        List<Integer> rejectedDriverIds = getRejectedDriverIds(bookingId);

        // Tự động dispatch driver tiếp theo
        // Lưu ý: phải dùng driverAccountId (khóa Account) chứ không phải driverId
        // (khóa Driver) — 2 giá trị này KHÔNG đảm bảo trùng nhau, truyền nhầm sẽ
        // khiến AuditLog của AUTO_DISPATCH_DRIVER/AUTO_DISPATCH_FAILED bị gán cho
        // sai tài khoản.
        autoDispatchNextDriver(bookingId, rejectedDriverIds, driverAccountId, ipAddress);
    }

    /**
     * Thông báo cho Dispatcher khi driver phản hồi lệnh dispatch (accept/reject).
     * accepted=true → "đã nhận chuyến", accepted=false → "đã từ chối" kèm lý do.
     */
    private void notifyDispatchersDriverResponded(int bookingId, int driverId,
            boolean accepted, String reason) throws Exception {
        java.util.List<Integer> dispatcherAccountIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
        if (dispatcherAccountIds.isEmpty())
            return;

        java.util.Map<String, String> driverInfo = new dao.DriverDAO().getDriverNameAndPhone(driverId);
        String driverName = driverInfo != null ? driverInfo.get("fullName") : ("Driver #" + driverId);

        String title;
        String message;
        String type;
        if (accepted) {
            title = "Booking #" + bookingId + " đã được tài xế nhận";
            message = "Tài xế " + driverName + " (DriverID=" + driverId + ") đã nhận booking #" + bookingId + ".";
            type = "BOOKING_DRIVER_ACCEPTED";
        } else {
            title = "Booking #" + bookingId + " bị tài xế từ chối";
            message = "Tài xế " + driverName + " (DriverID=" + driverId + ") đã từ chối booking #" + bookingId
                    + (reason != null && !reason.isEmpty() ? ". Lý do: " + reason : ".")
                    + " Hệ thống đang tự tìm tài xế khác.";
            type = "BOOKING_DRIVER_REJECTED";
        }

        dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
        for (int dispatcherAccountId : dispatcherAccountIds) {
            extDAO.createNotification(dispatcherAccountId, bookingId, title, message, type, "IN_APP");
        }
    }

    // ===================== Helpers =====================

    /**
     * Lấy tất cả DriverID đã REJECT broadcast của 1 booking — để tránh gán lại.
     */
    private List<Integer> getRejectedDriverIds(int bookingId) throws Exception {
        List<Integer> ids = new ArrayList<>();
        List<DriverJobBroadcast> history = broadcastDAO.getBroadcastHistory(bookingId);
        for (DriverJobBroadcast b : history) {
            if ("REJECTED".equalsIgnoreCase(b.getStatus())) {
                ids.add(b.getAssignedDriverId());
            }
        }
        return ids;
    }

    /**
     * Khi respondToDispatch trả về 0 dòng (broadcast không còn PENDING), xác định
     * lý do cụ thể để thông báo cho tài xế: nếu khách đã hủy đơn thì hiện thông
     * báo dễ hiểu thay vì message kỹ thuật chung chung.
     */
    private String buildDispatchFailureMessage(int broadcastId) {
        try {
            int bookingId = getBookingIdFromBroadcast(broadcastId);
            Booking booking = bookingDAO.findById(bookingId);
            if (booking != null && "CANCELLED".equalsIgnoreCase(booking.getStatus())) {
                return "Khách hàng đã hủy đơn. Tài xế vui lòng nhận chuyến khác.";
            }
        } catch (Exception ignore) {
            // Không xác định được lý do cụ thể — rơi về message mặc định bên dưới.
        }
        return "Không tìm thấy lệnh dispatch hợp lệ (đã được xử lý trước đó hoặc không thuộc driver này).";
    }

    private int getBookingIdFromBroadcast(int broadcastId) throws Exception {
        DriverJobBroadcast broadcast = broadcastDAO.findById(broadcastId);
        if (broadcast == null) {
            throw new IllegalArgumentException("Không tìm thấy broadcast #" + broadcastId);
        }
        return broadcast.getBookingId();
    }

    private Booking requireBookingInStatus(int bookingId, String expectedStatus, String errorMessage) throws Exception {
        Booking booking = bookingDAO.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Không tìm thấy booking #" + bookingId);
        }
        if (!expectedStatus.equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException(errorMessage + ". Trạng thái hiện tại: " + booking.getStatus());
        }
        return booking;
    }
}