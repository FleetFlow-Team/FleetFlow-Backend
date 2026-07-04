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
    public void approveBooking(int bookingId, int dispatcherAccountId, String ipAddress) throws Exception {
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
        Booking booking = requireBookingInStatus(bookingId, "PENDING",
                "Chỉ từ chối được booking đang ở trạng thái PENDING");

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "REJECTED");
                auditLogDAO.log(conn, dispatcherAccountId, "REJECT_BOOKING", "Booking",
                        String.valueOf(bookingId), booking.getStatus(),
                        "REJECTED" + (reason != null ? " (" + reason + ")" : ""), ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
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
            int triggeredByAccountId, String ipAddress) throws Exception {

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
            throw new IllegalArgumentException(
                    "Không tìm thấy lệnh dispatch hợp lệ (đã được xử lý trước đó hoặc không thuộc driver này).");
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
            throw new IllegalArgumentException(
                    "Không tìm thấy lệnh dispatch hợp lệ (đã được xử lý trước đó hoặc không thuộc driver này).");
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
        autoDispatchNextDriver(bookingId, rejectedDriverIds, driverId, ipAddress);
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