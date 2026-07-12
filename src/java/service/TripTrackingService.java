package service;

import dao.AuditLogDAO;
import dao.BookingDAO;
import dao.DriverJobBroadcastDAO;
import dao.TripTrackingDAO;
import java.math.BigDecimal;
import java.sql.Connection;
import model.Booking;
import model.TripGpsLog;
import utils.DbUtils;

/**
 * Service xử lý phần tiếp theo của lifecycle Booking sau khi Driver đã ACCEPT
 * (Booking.Status = CONFIRMED):
 *
 *   CONFIRMED → Driver bấm "start trip" → ONGOING
 *   ONGOING   → Driver đẩy GPS mỗi 30s (không đổi status)
 *   ONGOING   → Driver bấm "complete trip" → COMPLETED
 *
 * Mọi chuyển trạng thái đều ghi TripEventLog + AuditLog.
 */
public class TripTrackingService {

    private final BookingDAO bookingDAO = new BookingDAO();
    private final DriverJobBroadcastDAO broadcastDAO = new DriverJobBroadcastDAO();
    private final TripTrackingDAO trackingDAO = new TripTrackingDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    // ===================== Start trip =====================

    /**
     * Driver bấm bắt đầu chuyến đi — chỉ cho phép khi Booking đang CONFIRMED
     * và đúng driver đã được ACCEPT cho booking này (chống driver khác bấm start hộ).
     */
    public void startTrip(int bookingId, int driverId, String ipAddress) throws Exception {
        Booking booking = requireBookingInStatus(bookingId, "CONFIRMED",
                "Chỉ bắt đầu chuyến được khi booking đang CONFIRMED");

        requireDriverOwnsBooking(bookingId, driverId);

        // Kiểm tra khách đã đóng cọc 30% chưa — bắt buộc trước khi start
        // boolean depositPaid = new dao.ExtensionDAO().isDepositPaid(bookingId); // code cũ
        boolean depositPaid = new PaymentService().isDepositPaid(bookingId);
        if (!depositPaid) {
            throw new IllegalArgumentException(
                    "Khách hàng chưa đóng cọc 30%. Vui lòng yêu cầu khách thanh toán trước khi bắt đầu chuyến.");
        }

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "ONGOING");
                trackingDAO.insertEvent(conn, bookingId, "TRIP_STARTED", "Driver bắt đầu chuyến đi");
                int driverAccId = new dao.DriverDAO().getAccountIdByDriverId(driverId);
                auditLogDAO.log(conn, driverAccId, "START_TRIP", "Booking",
                        String.valueOf(bookingId), booking.getStatus(), "ONGOING", ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ===================== Complete trip =====================

    /**
     * Driver bấm hoàn thành chuyến đi — chỉ cho phép khi Booking đang ONGOING.
     */
    public void completeTrip(int bookingId, int driverId, String ipAddress) throws Exception {
        Booking booking = requireBookingInStatus(bookingId, "ONGOING",
                "Chỉ hoàn thành chuyến được khi booking đang ONGOING");

        requireDriverOwnsBooking(bookingId, driverId);

        // Khách phải trả xong phần còn lại (70%) TRƯỚC khi tài xế được bấm hoàn thành —
        // khách có thể trả ngay trong lúc ONGOING (xem VNPayController/FinalPaymentController,
        // đã bỏ điều kiện bắt buộc COMPLETED để cho phép trả sớm hơn).
        java.math.BigDecimal remaining = new PaymentService().remainingOf(bookingId);
        if (remaining.compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(
                    "Khách hàng chưa thanh toán " + remaining.toPlainString()
                            + "đ còn lại. Vui lòng yêu cầu khách thanh toán trước khi hoàn thành chuyến.");
        }

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.updateStatus(conn, bookingId, "COMPLETED");
                trackingDAO.insertEvent(conn, bookingId, "TRIP_COMPLETED", "Driver hoàn thành chuyến đi");
                int driverAccId2 = new dao.DriverDAO().getAccountIdByDriverId(driverId);
                auditLogDAO.log(conn, driverAccId2, "COMPLETE_TRIP", "Booking",
                        String.valueOf(bookingId), booking.getStatus(), "COMPLETED", ipAddress);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // Notify customer: chuyến đã hoàn thành + yêu cầu thanh toán phần còn lại
        try {
            dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
            int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                // Số còn nợ đã được đảm bảo <= 0 ở guard đầu hàm (completeTrip chặn nếu còn nợ),
                // nên tới đây luôn là "đã thanh toán đủ" — không cần tính lại remainingOf.
                String message = "Chuyến đi #" + bookingId + " đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!";
                extDAO.createNotification(customerAccountId, bookingId,
                        "Chuyến đi đã hoàn thành - Yêu cầu thanh toán",
                        message,
                        "TRIP_COMPLETED_PAYMENT_REQUIRED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== GPS tracking =====================

    /**
     * Driver đẩy 1 điểm GPS mới — chỉ cho phép khi booking đang ONGOING.
     * Không update Booking.Status, chỉ ghi log vị trí.
     */
    public void pushGpsLocation(int bookingId, int driverId, BigDecimal lat, BigDecimal lng) throws Exception {
        requireBookingInStatus(bookingId, "ONGOING",
                "Chỉ ghi nhận GPS khi booking đang ONGOING (chuyến đã bắt đầu)");

        requireDriverOwnsBooking(bookingId, driverId);

        trackingDAO.insertGpsLog(bookingId, lat, lng);
    }

    /**
     * Dispatcher xem vị trí hiện tại của TẤT CẢ booking đang ONGOING trên bản đồ tổng quan.
     */
    public java.util.List<TripGpsLog> getOngoingTripsMap() throws Exception {
        return trackingDAO.getLatestGpsForOngoingBookings();
    }

    // ===================== Helpers =====================

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

    /**
     * Chống driver khác bấm start/complete/gps hộ — kiểm tra đúng driver
     * đã được ACCEPT cho booking này (source of truth: DriverJobBroadcast).
     */
    private void requireDriverOwnsBooking(int bookingId, int driverId) throws Exception {
        int acceptedDriverId = broadcastDAO.getAcceptedDriverId(bookingId);
        if (acceptedDriverId == -1) {
            throw new IllegalArgumentException("Booking này chưa có driver nào được xác nhận (ACCEPTED).");
        }
        if (acceptedDriverId != driverId) {
            throw new IllegalArgumentException("Booking này không thuộc driver đang đăng nhập.");
        }
    }
}