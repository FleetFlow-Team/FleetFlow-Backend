package service;

import dao.AccountDAO;
import dao.BookingDAO;
import dao.BookingExtensionDAO;
import dao.ExtensionDAO;
import dao.TripTrackingDAO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import model.Booking;
import model.BookingDetail;
import model.BookingExtension;
import model.BookingPricing;
import utils.DbUtils;

/**
 * Nghiệp vụ gia hạn & quá giờ — CHỈ áp dụng cho booking HOURLY/DAILY đang
 * ONGOING (xem NGHIỆP VỤ GIA HẠN & QUÁ GIỜ đã chốt). DISTANCE/INNER_CITY/
 * INTER_CITY không có khái niệm ReturnTime mở nên không áp dụng.
 *
 * LUỒNG 1 (REQUESTED — chủ động xin trước):
 *   - 2 cửa đối xứng: Khách xin -> Tài xế + Dispatcher duyệt;
 *                      Tài xế xin hộ -> Khách + Dispatcher duyệt.
 *   - Auto-check: xe trống phía sau (buffer 60p) + ngân sách gộp chung
 *     (2h HOURLY / 1 ngày DAILY, tính cả REQUESTED-APPROVED lẫn RETROACTIVE).
 *   - Cần ĐỦ 2 phiếu đồng ý trong 1 cửa sổ 10 phút chung; 1 phiếu từ chối
 *     là hỏng ngay, không cần đợi bên kia.
 *
 * LUỒNG 2 (RETROACTIVE — quá giờ không xin trước):
 *   - Grace 15 phút đầu miễn phí, không notify.
 *   - Qua grace: notify đúng 1 lần (idempotent qua TripEventLog, key theo
 *     ReturnTime hiện hành — dời hạn là 1 vòng đếm mới).
 *   - Cap động = min(trần cứng - đã dùng, khoảng trống chuyến kế - buffer 60p).
 *   - Chốt sổ lúc completeTrip: tính lố theo giờ tròn từ ReturnTime hiện
 *     hành, tự động APPROVED, cộng tiền, dời ReturnTime = giờ kết thúc thật.
 *
 * Không có scheduler riêng trong hệ thống -> mọi mốc thời gian được kiểm
 * tra kiểu "lazy" (ăn theo GPS ping 30s có sẵn khi ONGOING), KHÔNG dựa vào
 * cron/job nền.
 */
public class BookingExtensionService {

    private static final int PENDING_WINDOW_MINUTES = 10;
    private static final int BUFFER_MINUTES = 60;
    private static final int GRACE_MINUTES = 15;
    private static final int HARD_CAP_HOURLY_MINUTES = 2 * 60;
    private static final int HARD_CAP_DAILY_MINUTES = 24 * 60;

    private final BookingDAO bookingDAO = new BookingDAO();
    private final BookingExtensionDAO extensionDAO = new BookingExtensionDAO();
    private final ExtensionDAO notifyDAO = new ExtensionDAO(); // tên cũ, thật ra là helper notify/lookup account
    private final TripTrackingDAO trackingDAO = new TripTrackingDAO();
    private final AccountDAO accountDAO = new AccountDAO();

    // ===================== LUỒNG 1 =====================

    public int requestExtension(int bookingId, String requestedByRole, int requestedByAccountId, int extraUnits) throws Exception {
        if (extraUnits <= 0) {
            throw new IllegalArgumentException("Số giờ/ngày xin gia hạn phải > 0.");
        }
        if (!"CUSTOMER".equalsIgnoreCase(requestedByRole) && !"DRIVER".equalsIgnoreCase(requestedByRole)) {
            throw new IllegalArgumentException("requestedByRole phải là CUSTOMER hoặc DRIVER.");
        }

        Booking booking = requireEligibleBooking(bookingId);
        boolean isHourly = "HOURLY".equalsIgnoreCase(booking.getBookingType());

        int customerAccountId = notifyDAO.getCustomerAccountIdByBookingId(bookingId);
        int driverAccountId = notifyDAO.getDriverAccountIdByBookingId(bookingId);
        if ("CUSTOMER".equalsIgnoreCase(requestedByRole) && requestedByAccountId != customerAccountId) {
            throw new IllegalArgumentException("Bạn không phải khách hàng của booking này.");
        }
        if ("DRIVER".equalsIgnoreCase(requestedByRole) && requestedByAccountId != driverAccountId) {
            throw new IllegalArgumentException("Bạn không phải tài xế của booking này.");
        }

        String counterpartyRole = "CUSTOMER".equalsIgnoreCase(requestedByRole) ? "DRIVER" : "CUSTOMER";

        BookingDetail detail = bookingDAO.findDetailByBookingId(bookingId);
        Timestamp oldReturnTime = detail.getReturnTime();
        int unitMinutes = isHourly ? 60 : (24 * 60);
        int extraMinutes = extraUnits * unitMinutes;
        Timestamp newReturnTime = new Timestamp(oldReturnTime.getTime() + extraMinutes * 60_000L);

        int hardCap = isHourly ? HARD_CAP_HOURLY_MINUTES : HARD_CAP_DAILY_MINUTES;
        int usedMinutes = extensionDAO.getUsedBudgetMinutes(bookingId);
        if (usedMinutes + extraMinutes > hardCap) {
            throw new IllegalArgumentException(
                    "Vượt trần gia hạn cho phép (" + (hardCap / 60) + (isHourly ? " giờ" : "/24h = 1 ngày")
                    + "). Đã dùng " + usedMinutes + " phút, xin thêm " + extraMinutes
                    + " phút sẽ vượt trần. Muốn thuê thêm, vui lòng đặt booking mới.");
        }

        Timestamp checkEnd = new Timestamp(newReturnTime.getTime() + BUFFER_MINUTES * 60_000L);
        try (Connection conn = DbUtils.getConnection()) {
            if (bookingDAO.isVehicleScheduleConflict(conn, booking.getVehicleId(), oldReturnTime, checkEnd, bookingId)) {
                throw new IllegalArgumentException(
                        "Xe đã có lịch chạy khác gần khung giờ gia hạn này. Không thể gia hạn thêm.");
            }
        }

        BookingPricing pricing = bookingDAO.findPricingByBookingId(bookingId);
        BigDecimal unitPrice = isHourly ? pricing.getPricePerHourSnapshot() : pricing.getPricePerDaySnapshot();
        if (unitPrice == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn giá gốc của booking này để tính tiền gia hạn.");
        }
        BigDecimal extraAmount = unitPrice.multiply(BigDecimal.valueOf(extraUnits));

        int extensionId = extensionDAO.createRequested(bookingId, requestedByRole.toUpperCase(), requestedByAccountId,
                counterpartyRole, oldReturnTime, newReturnTime, extraMinutes, extraAmount, PENDING_WINDOW_MINUTES);

        notifyExtensionCreated(bookingId, extensionId, counterpartyRole, customerAccountId, driverAccountId,
                extraUnits, isHourly, extraAmount);

        return extensionId;
    }

    public void respondAsCounterparty(int extensionId, int accountId, boolean approve) throws Exception {
        BookingExtension ext = loadAndExpireIfNeeded(extensionId);

        int expectedAccountId = "CUSTOMER".equalsIgnoreCase(ext.getCounterpartyRole())
                ? notifyDAO.getCustomerAccountIdByBookingId(ext.getBookingId())
                : notifyDAO.getDriverAccountIdByBookingId(ext.getBookingId());
        if (accountId != expectedAccountId) {
            throw new IllegalArgumentException("Bạn không phải người cần xác nhận yêu cầu gia hạn này.");
        }

        boolean applied = extensionDAO.respondCounterparty(extensionId, accountId, approve);
        if (!applied) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý (hoặc đã hết hạn) trước đó rồi.");
        }

        afterVote(extensionId, approve);
    }

    public void respondAsDispatcher(int extensionId, int dispatcherAccountId, boolean approve) throws Exception {
        loadAndExpireIfNeeded(extensionId);

        List<Integer> activeDispatchers = accountDAO.getActiveDispatcherAccountIds();
        if (!activeDispatchers.contains(dispatcherAccountId)) {
            throw new IllegalArgumentException("Tài khoản này không phải Dispatcher đang hoạt động.");
        }

        boolean applied = extensionDAO.respondDispatcher(extensionId, dispatcherAccountId, approve);
        if (!applied) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý (hoặc đã hết hạn) trước đó rồi.");
        }

        afterVote(extensionId, approve);
    }

    private BookingExtension loadAndExpireIfNeeded(int extensionId) throws Exception {
        BookingExtension ext = extensionDAO.getById(extensionId);
        if (ext == null) {
            throw new IllegalArgumentException("Không tìm thấy yêu cầu gia hạn #" + extensionId);
        }
        if ("PENDING".equalsIgnoreCase(ext.getStatus())) {
            boolean expired = extensionDAO.expireIfOverdue(extensionId);
            if (expired) {
                notifyResolution(ext, "EXPIRED");
                throw new IllegalArgumentException("Yêu cầu gia hạn đã hết hạn 10 phút chờ (tự động EXPIRED).");
            }
        }
        return ext;
    }

    private void afterVote(int extensionId, boolean approve) throws Exception {
        BookingExtension ext = extensionDAO.getById(extensionId);
        if (!approve || "REJECTED".equalsIgnoreCase(ext.getStatus())) {
            notifyResolution(ext, "REJECTED");
            return;
        }
        if ("APPROVED".equalsIgnoreCase(ext.getCounterpartyStatus())
                && "APPROVED".equalsIgnoreCase(ext.getDispatcherStatus())) {
            finalizeAndApply(ext);
        } else {
            notifyPartialApproval(ext);
        }
    }

    private void finalizeAndApply(BookingExtension ext) throws Exception {
        int bookingId = ext.getBookingId();
        Booking booking = bookingDAO.findById(bookingId);

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bookingDAO.lockVehicleRow(conn, booking.getVehicleId());

                Timestamp checkEnd = new Timestamp(ext.getNewReturnTime().getTime() + BUFFER_MINUTES * 60_000L);
                boolean conflict = bookingDAO.isVehicleScheduleConflict(
                        conn, booking.getVehicleId(), ext.getOldReturnTime(), checkEnd, bookingId);

                if (conflict) {
                    forceRejectDueToConflict(conn, ext.getId());
                    conn.commit();
                    notifyResolution(extensionDAO.getById(ext.getId()), "REJECTED_LATE_CONFLICT");
                    return;
                }

                boolean weFinalized = extensionDAO.finalizeApproved(conn, ext.getId());
                if (weFinalized) {
                    bookingDAO.updateReturnTime(conn, bookingId, ext.getNewReturnTime());
                    bookingDAO.addToEstimatedTotal(conn, bookingId, ext.getExtraAmount());
                }
                conn.commit();

                if (weFinalized) {
                    notifyResolution(extensionDAO.getById(ext.getId()), "APPROVED");
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void forceRejectDueToConflict(Connection conn, int extensionId) throws Exception {
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "UPDATE BookingExtension SET Status='REJECTED', ResolvedAt=GETDATE(), "
                + "Notes='He thong phat hien xung dot lich xe vao phut chot, tu dong tu choi.' "
                + "WHERE ExtensionID=? AND Status='PENDING'")) {
            ps.setInt(1, extensionId);
            ps.executeUpdate();
        }
    }

    // ===================== LUỒNG 2 — RETROACTIVE (quá giờ) =====================

    public void checkOvertimeTouchpoint(int bookingId) throws Exception {
        Booking booking = bookingDAO.findById(bookingId);
        if (booking == null || !"ONGOING".equalsIgnoreCase(booking.getStatus())) {
            return;
        }
        boolean isHourly = "HOURLY".equalsIgnoreCase(booking.getBookingType());
        boolean isDaily = "DAILY".equalsIgnoreCase(booking.getBookingType());
        if (!isHourly && !isDaily) {
            return;
        }

        BookingDetail detail = bookingDAO.findDetailByBookingId(bookingId);
        Timestamp returnTime = detail.getReturnTime();
        if (returnTime == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long graceEndMillis = returnTime.getTime() + GRACE_MINUTES * 60_000L;
        if (now <= graceEndMillis) {
            return;
        }

        String returnTimeKey = "ReturnTime=" + returnTime.getTime();

        boolean alreadyNotified = trackingDAO.hasEvent(bookingId, "OVERTIME_NOTIFIED", returnTimeKey);
        if (!alreadyNotified) {
            trackingDAO.insertEvent(bookingId, "OVERTIME_NOTIFIED", returnTimeKey);
            notifyOvertimeStarted(bookingId, booking);
        }

        int hardCap = isHourly ? HARD_CAP_HOURLY_MINUTES : HARD_CAP_DAILY_MINUTES;
        int usedMinutes;
        try (Connection conn = DbUtils.getConnection()) {
            usedMinutes = extensionDAO.getUsedBudgetMinutes(conn, bookingId);
        }
        int remainingBudget = Math.max(0, hardCap - usedMinutes);

        Integer gapMinutes = null;
        try (Connection conn = DbUtils.getConnection()) {
            Timestamp nextDeparture = bookingDAO.getNextBookingDeparture(conn, booking.getVehicleId(), returnTime, bookingId);
            if (nextDeparture != null) {
                gapMinutes = (int) ((nextDeparture.getTime() - returnTime.getTime()) / 60_000L);
            }
        }
        int dynamicCapMinutes = remainingBudget;
        if (gapMinutes != null) {
            dynamicCapMinutes = Math.min(dynamicCapMinutes, Math.max(0, gapMinutes - BUFFER_MINUTES));
        }

        long elapsedMinutes = (now - returnTime.getTime()) / 60_000L;

        boolean capReached = elapsedMinutes >= dynamicCapMinutes;
        boolean alreadyCapNotified = trackingDAO.hasEvent(bookingId, "OVERTIME_CAP_REACHED", returnTimeKey);
        if (capReached && !alreadyCapNotified) {
            trackingDAO.insertEvent(bookingId, "OVERTIME_CAP_REACHED", returnTimeKey);
            notifyOvertimeCapReached(bookingId, booking, dynamicCapMinutes);
        }
    }

    public void settleOvertimeOnComplete(int bookingId) throws Exception {
        Booking booking = bookingDAO.findById(bookingId);
        boolean isHourly = "HOURLY".equalsIgnoreCase(booking.getBookingType());
        boolean isDaily = "DAILY".equalsIgnoreCase(booking.getBookingType());
        if (!isHourly && !isDaily) {
            return;
        }

        BookingDetail detail = bookingDAO.findDetailByBookingId(bookingId);
        Timestamp returnTime = detail.getReturnTime();
        long now = System.currentTimeMillis();
        long elapsedMinutes = (now - returnTime.getTime()) / 60_000L;

        if (elapsedMinutes <= GRACE_MINUTES) {
            return;
        }

        long overtimeHoursRoundedUp = (long) Math.ceil(elapsedMinutes / 60.0);
        int billedMinutes = (int) (overtimeHoursRoundedUp * 60);

        BookingPricing pricing = bookingDAO.findPricingByBookingId(bookingId);
        BigDecimal pricePerHour = pricing.getPricePerHourSnapshot();
        if (pricePerHour == null) {
            trackingDAO.insertEvent(bookingId, "OVERTIME_SETTLE_FAILED_NO_SNAPSHOT",
                    "Lo " + overtimeHoursRoundedUp + " gio nhung thieu PricePerHourSnapshot, can dispatcher tinh tay.");
            return;
        }
        BigDecimal extraAmount = pricePerHour.multiply(BigDecimal.valueOf(overtimeHoursRoundedUp))
                .setScale(2, RoundingMode.HALF_UP);

        Timestamp newReturnTime = new Timestamp(now);

        try (Connection conn = DbUtils.getConnection()) {
            conn.setAutoCommit(false);
            try {
                extensionDAO.createRetroactive(conn, bookingId, returnTime, newReturnTime, billedMinutes, extraAmount,
                        "Tu dong chot qua gio luc hoan tat chuyen (lo " + overtimeHoursRoundedUp + " gio, lam tron len).");
                bookingDAO.updateReturnTime(conn, bookingId, newReturnTime);
                bookingDAO.addToEstimatedTotal(conn, bookingId, extraAmount);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        notifyOvertimeSettled(bookingId, overtimeHoursRoundedUp, extraAmount);
    }

    // ===================== Helpers =====================

    private Booking requireEligibleBooking(int bookingId) throws Exception {
        Booking booking = bookingDAO.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Không tìm thấy booking #" + bookingId);
        }
        boolean isHourly = "HOURLY".equalsIgnoreCase(booking.getBookingType());
        boolean isDaily = "DAILY".equalsIgnoreCase(booking.getBookingType());
        if (!isHourly && !isDaily) {
            throw new IllegalArgumentException(
                    "Gia hạn chỉ áp dụng cho booking HOURLY/DAILY. Booking này là " + booking.getBookingType() + ".");
        }
        if (!"ONGOING".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("Chỉ gia hạn được khi chuyến đang ONGOING. Trạng thái hiện tại: " + booking.getStatus());
        }
        return booking;
    }

    private void notifyExtensionCreated(int bookingId, int extensionId, String counterpartyRole,
            int customerAccountId, int driverAccountId, int extraUnits, boolean isHourly, BigDecimal extraAmount) {
        try {
            String unitLabel = isHourly ? "giờ" : "ngày";
            int counterpartyAccountId = "CUSTOMER".equalsIgnoreCase(counterpartyRole) ? customerAccountId : driverAccountId;
            if (counterpartyAccountId != -1) {
                notifyDAO.createNotification(counterpartyAccountId, bookingId,
                        "Yêu cầu gia hạn chuyến #" + bookingId,
                        "Có yêu cầu gia hạn thêm " + extraUnits + " " + unitLabel
                                + " (phát sinh " + extraAmount.toPlainString() + "đ). Bạn có 10 phút để xác nhận.",
                        "EXTENSION_REQUESTED", "IN_APP");
            }
            for (int dispId : accountDAO.getActiveDispatcherAccountIds()) {
                notifyDAO.createNotification(dispId, bookingId,
                        "Yêu cầu gia hạn chuyến #" + bookingId + " cần duyệt",
                        "Booking #" + bookingId + " xin gia hạn thêm " + extraUnits + " " + unitLabel
                                + ". Vui lòng duyệt trong 10 phút (extensionId=" + extensionId + ").",
                        "EXTENSION_REQUESTED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyPartialApproval(BookingExtension ext) {
        try {
            String waitingFor = "APPROVED".equalsIgnoreCase(ext.getCounterpartyStatus()) ? "Dispatcher" : ext.getCounterpartyRole();
            for (int dispId : accountDAO.getActiveDispatcherAccountIds()) {
                notifyDAO.createNotification(dispId, ext.getBookingId(),
                        "Gia hạn #" + ext.getId() + " đã có 1 phiếu đồng ý",
                        "Đang chờ " + waitingFor + " xác nhận nốt trong cửa sổ 10 phút.",
                        "EXTENSION_PARTIAL_APPROVED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyResolution(BookingExtension ext, String outcome) {
        try {
            int customerAccountId = notifyDAO.getCustomerAccountIdByBookingId(ext.getBookingId());
            int driverAccountId = notifyDAO.getDriverAccountIdByBookingId(ext.getBookingId());
            String title;
            String body;
            switch (outcome) {
                case "APPROVED":
                    title = "Gia hạn đã được duyệt";
                    body = "Chuyến #" + ext.getBookingId() + " đã gia hạn tới " + ext.getNewReturnTime()
                            + ". Phát sinh thêm " + ext.getExtraAmount().toPlainString() + "đ (cộng vào bill cuối chuyến).";
                    break;
                case "REJECTED":
                    title = "Gia hạn bị từ chối";
                    body = "Yêu cầu gia hạn chuyến #" + ext.getBookingId() + " đã bị từ chối.";
                    break;
                case "REJECTED_LATE_CONFLICT":
                    title = "Gia hạn không thực hiện được";
                    body = "Yêu cầu gia hạn chuyến #" + ext.getBookingId()
                            + " đã được đồng ý nhưng phát hiện xung đột lịch xe vào phút chót nên không thể áp dụng.";
                    break;
                default:
                    title = "Gia hạn đã hết hạn chờ";
                    body = "Yêu cầu gia hạn chuyến #" + ext.getBookingId() + " đã hết 10 phút chờ, tự động hủy.";
            }
            if (customerAccountId != -1) {
                notifyDAO.createNotification(customerAccountId, ext.getBookingId(), title, body, "EXTENSION_" + outcome, "IN_APP");
            }
            if (driverAccountId != -1) {
                notifyDAO.createNotification(driverAccountId, ext.getBookingId(), title, body, "EXTENSION_" + outcome, "IN_APP");
            }
            for (int dispId : accountDAO.getActiveDispatcherAccountIds()) {
                notifyDAO.createNotification(dispId, ext.getBookingId(), title, body, "EXTENSION_" + outcome, "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyOvertimeStarted(int bookingId, Booking booking) {
        try {
            int customerAccountId = notifyDAO.getCustomerAccountIdByBookingId(bookingId);
            int driverAccountId = notifyDAO.getDriverAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                notifyDAO.createNotification(customerAccountId, bookingId, "Chuyến đang bị tính phí quá giờ",
                        "Chuyến #" + bookingId + " đã quá giờ trả xe, đang được tính phí theo giờ.",
                        "OVERTIME_STARTED", "IN_APP");
            }
            if (driverAccountId != -1) {
                notifyDAO.createNotification(driverAccountId, bookingId, "Chuyến đang kéo dài",
                        "Chuyến #" + bookingId + " đã quá ReturnTime. Nhắc khách nếu cần gia hạn chính thức.",
                        "OVERTIME_STARTED", "IN_APP");
            }
            for (int dispId : accountDAO.getActiveDispatcherAccountIds()) {
                notifyDAO.createNotification(dispId, bookingId, "Chuyến #" + bookingId + " đang quá giờ",
                        "Vehicle #" + booking.getVehicleId() + " đang chạy quá ReturnTime.",
                        "OVERTIME_STARTED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyOvertimeCapReached(int bookingId, Booking booking, int capMinutes) {
        try {
            String urgency = capMinutes <= 0
                    ? "KHẨN: chuyến kế tiếp của xe này sát giờ, cần xử lý ngay (gọi khách hoặc đổi xe)."
                    : "Đã chạm trần quá giờ cho phép (" + capMinutes + " phút).";
            for (int dispId : accountDAO.getActiveDispatcherAccountIds()) {
                notifyDAO.createNotification(dispId, bookingId, "Chuyến #" + bookingId + " chạm trần quá giờ",
                        urgency + " Vehicle #" + booking.getVehicleId() + ".",
                        "OVERTIME_CAP_REACHED", "IN_APP");
            }
            int customerAccountId = notifyDAO.getCustomerAccountIdByBookingId(bookingId);
            int driverAccountId = notifyDAO.getDriverAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                notifyDAO.createNotification(customerAccountId, bookingId, "Cần trả xe hoặc gia hạn ngay",
                        "Chuyến #" + bookingId + " đã chạm giới hạn quá giờ, vui lòng liên hệ tổng đài.",
                        "OVERTIME_CAP_REACHED", "IN_APP");
            }
            if (driverAccountId != -1) {
                notifyDAO.createNotification(driverAccountId, bookingId, "Cần xử lý ngay",
                        "Chuyến #" + bookingId + " đã chạm trần quá giờ, liên hệ dispatcher.",
                        "OVERTIME_CAP_REACHED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyOvertimeSettled(int bookingId, long overtimeHours, BigDecimal extraAmount) {
        try {
            int customerAccountId = notifyDAO.getCustomerAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                notifyDAO.createNotification(customerAccountId, bookingId, "Phí quá giờ đã được cộng vào bill",
                        "Chuyến #" + bookingId + " đã trễ " + overtimeHours + " giờ, phát sinh thêm "
                                + extraAmount.toPlainString() + "đ. Vui lòng thanh toán đủ để hoàn tất chuyến.",
                        "OVERTIME_SETTLED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}