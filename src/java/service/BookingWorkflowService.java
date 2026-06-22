package service;

import controller.DriverDispatchController;
import dao.AuditLogDAO;
import dao.BookingDAO;
import dao.DriverJobBroadcastDAO;
import java.sql.Connection;
import model.Booking;
import model.DriverJobBroadcast;
import utils.DbUtils;

/**
 * Service điều phối toàn bộ luồng sau khi Customer tạo Booking:
 *
 *   PENDING (Customer tạo xong)
 *     → Dispatcher APPROVE  → APPROVED
 *     → Dispatcher REJECT   → REJECTED
 *
 *   APPROVED
 *     → Dispatcher dispatch driver → DriverJobBroadcast (PENDING) → Booking.Status = DISPATCHED
 *
 *   DISPATCHED
 *     → Driver ACCEPT  → DriverJobBroadcast.Status=ACCEPTED → Booking.Status = CONFIRMED
 *     → Driver REJECT  → DriverJobBroadcast.Status=REJECTED → Booking.Status quay lại APPROVED
 *                         (để Dispatcher dispatch driver khác)
 *
 * Mọi hành động duyệt/từ chối/dispatch đều ghi vào AuditLog — không thêm cột
 * ApprovedBy/ApprovedAt riêng ở Booking.
 */
public class BookingWorkflowService {

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverJobBroadcastDAO broadcastDAO = new DriverJobBroadcastDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    // ===================== BƯỚC 1: Dispatcher duyệt/từ chối Booking =====================

    /**
     * Dispatcher duyệt booking — chỉ chuyển PENDING → APPROVED.
     */
    public void approveBooking(int bookingId, int dispatcherAccountId, String ipAddress) throws Exception {
        Booking booking = requireBookingInStatus(bookingId, "PENDING",
                "Chỉ duyệt được booking đang ở trạng thái PENDING");

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
    }

    /**
     * Dispatcher từ chối booking — chỉ chuyển PENDING → REJECTED.
     */
    public void rejectBooking(int bookingId, int dispatcherAccountId, String reason, String ipAddress) throws Exception {
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

    // ===================== BƯỚC 2: Dispatcher dispatch driver =====================

    /**
     * Dispatcher chỉ định 1 driver cụ thể cho booking đã APPROVED.
     * Tạo DriverJobBroadcast (PENDING) + chuyển Booking sang DISPATCHED.
     * Chặn dispatch trùng nếu booking đang có broadcast PENDING khác.
     */
    public long dispatchDriver(int bookingId, int driverId, int dispatcherAccountId, String ipAddress) throws Exception {
        Booking booking = requireBookingInStatus(bookingId, "APPROVED",
                "Chỉ dispatch driver được khi booking đã APPROVED");

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
                        "DISPATCHED (driverId=" + driverId + ")", ipAddress);
                conn.commit();
                return broadcastId;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ===================== BƯỚC 3: Driver phản hồi lệnh dispatch =====================

    /**
     * Driver accept lệnh dispatch → Booking chuyển CONFIRMED.
     */
    public void driverAccept(int broadcastId, int driverId, String ipAddress) throws Exception {
        respondToDispatch(broadcastId, driverId, "ACCEPTED", "CONFIRMED", ipAddress, "DRIVER_ACCEPT");
    }

    /**
     * Driver reject lệnh dispatch → Booking quay lại APPROVED để Dispatcher dispatch driver khác.
     */
    /**
     * Driver reject lệnh dispatch → Booking quay lại APPROVED để Dispatcher dispatch driver khác.
     * Lý do reject được ghi vào AuditLog.NewValue, không thêm cột riêng ở DriverJobBroadcast.
     */
    public void driverReject(int broadcastId, int driverId, String reason, String ipAddress) throws Exception {
        respondToDispatch(broadcastId, driverId, "REJECTED", "APPROVED", ipAddress, "DRIVER_REJECT", reason);
    }

    private void respondToDispatch(int broadcastId, int driverId, String broadcastStatus,
            String bookingStatus, String ipAddress, String auditAction) throws Exception {
        respondToDispatch(broadcastId, driverId, broadcastStatus, bookingStatus, ipAddress, auditAction, null);
    }

    private void respondToDispatch(int broadcastId, int driverId, String broadcastStatus,
            String bookingStatus, String ipAddress, String auditAction, String reason) throws Exception {

        int updated = broadcastDAO.respondToDispatch(broadcastId, driverId, broadcastStatus);
        if (updated == 0) {
            throw new IllegalArgumentException(
                "Không tìm thấy lệnh dispatch hợp lệ (đã được xử lý trước đó hoặc không thuộc driver này).");
        }

        int bookingId = getBookingIdFromBroadcast(broadcastId);
        // auditAction (DRIVER_REJECT/DRIVER_ACCEPT) đã thể hiện rõ hành động;
        // newValueLog chỉ nên ghi rõ "Status mới (do hành động X, lý do: ...)" tránh hiểu lầm
        String newValueLog = bookingStatus
                + (reason != null && !reason.trim().isEmpty() ? " — " + auditAction + ", lý do: " + reason : "");

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, bookingStatus);
                auditLogDAO.log(conn, driverId, auditAction, "Booking",
                        String.valueOf(bookingId), "DISPATCHED", newValueLog, ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int getBookingIdFromBroadcast(int broadcastId) throws Exception {
        DriverJobBroadcast broadcast = broadcastDAO.findById(broadcastId);
        if (broadcast == null) {
            throw new IllegalArgumentException("Không tìm thấy broadcast #" + broadcastId);
        }
        return broadcast.getBookingId();
    }

    // ===================== Helper =====================

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