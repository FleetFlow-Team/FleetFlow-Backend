package service;

import model.Booking;
import dao.CustomerBookingDAO;
import dao.NotificationDAO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import dao.CustomerBookingDAO.BookingRow;
import model.PricingRule;
import model.Voucher;

public class CustomerBookingService {

    private final CustomerBookingDAO dao = new CustomerBookingDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    // ===== Hằng số quy tắc nghiệp vụ (tập trung, tránh magic number rải rác) =====
    /** Tỉ lệ đặt cọc trên tổng tiền: cọc = 30% EstimatedTotal. */
    public static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.30");
    /** Hủy khi còn < N giờ trước giờ khởi hành → mất cọc (BR-12). */
    public static final int CANCELLATION_FORFEIT_HOURS = 12;
    /** Hủy trong vòng N phút sau khi tạo booking → luôn miễn phí (grace period). */
    public static final int CANCELLATION_GRACE_MINUTES = 10;

    // ===================== BE-23: Lịch sử đặt xe =====================
    public List<BookingRow> getBookingHistory(int customerId) throws Exception {
        return dao.getBookingsByCustomerId(customerId);
    }

    // ===================== BE-25: Cancel + tính phạt =====================
    // Logic phạt (BR-12): phạt = MẤT CỌC, không tính % tổng tiền
    // Hủy trước >= 12h  → không mất gì
    // Hủy trước < 12h   → mất nguyên tiền cọc (30% tổng tiền đã đặt)
    // Hủy trong 10 phút sau khi tạo booking → luôn miễn phí (grace period)
    // Cọc là tiền đã trả bị tịch thu → KHÔNG ghi công nợ vào CustomerWallet
    // Booking đang COMPLETED/CANCELLED → không cho hủy
    public CancelResult cancelBooking(int bookingId, int customerId, String reason) throws Exception {
        Booking booking = dao.findBookingById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Không tìm thấy booking");
        }
        if (booking.getCustomerId() != customerId) {
            throw new IllegalArgumentException("Booking không thuộc customer này");
        }
        if ("CANCELLED".equals(booking.getStatus()) || "COMPLETED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking đã " + booking.getStatus() + ", không thể hủy");
        }

        // Lấy departureTime từ note (đã gắn trong DAO)
        Timestamp departureTime = null;
        String note = booking.getNote();
        if (note != null && note.contains("departureTime=")) {
            String dtStr = note.replace("departureTime=", "").trim();
            if (!"null".equals(dtStr)) {
                departureTime = Timestamp.valueOf(dtStr);
            }
        }

        // Tính phạt
        // Grace period: hủy trong vòng 10 phút sau khi tạo booking thì luôn miễn phí,
        // không phụ thuộc còn bao lâu tới giờ khởi hành — tránh xung đột với rule đặt xe
        // tối thiểu trước 120 phút (BR-02), vì user có thể bấm nhầm ngay sau khi đặt.
        boolean isForfeitDeposit = false;
        if (departureTime != null) {
            long now = System.currentTimeMillis();
            long minutesSinceCreated = (now - booking.getCreatedAt().getTime()) / (1000 * 60);

            if (minutesSinceCreated > CANCELLATION_GRACE_MINUTES) {
                long hoursUntilDeparture = (departureTime.getTime() - now) / (1000 * 60 * 60);
                // BR-12: >=12h trước giờ khởi hành → free; <12h → mất cọc
                isForfeitDeposit = hoursUntilDeparture < CANCELLATION_FORFEIT_HOURS;
            }
        }

        // Lấy giá booking hiện tại để tính tiền cọc (30% tổng tiền — khớp công thức
        // tính deposit ở BE-26 calculatePrice)
        BigDecimal totalAmount = BigDecimal.ZERO;
        try {
            totalAmount = dao.getBookingTotalAmount(bookingId);
        } catch (Exception e) {
            System.err.println("Không lấy được giá booking: " + e.getMessage());
        }

        BigDecimal depositAmount = totalAmount
                .multiply(DEPOSIT_RATE)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal penaltyAmount = isForfeitDeposit ? depositAmount : BigDecimal.ZERO;

        dao.cancelBookingWithPenalty(
                bookingId,
                customerId,
                isForfeitDeposit,
                penaltyAmount,
                reason
        );

        try {
            int customerAccountId = notificationDAO.resolveCustomerAccountByCustomerId(customerId);
            if (customerAccountId != -1) {
                String msg = "Chuyến #" + bookingId + " đã được hủy thành công.";
                if (isForfeitDeposit) {
                    msg += " Bạn bị mất tiền cọc " + penaltyAmount.toPlainString()
                            + " đ do hủy trong vòng 12h trước giờ khởi hành.";
                } else {
                    msg += " Bạn không bị mất phí cho lần hủy này.";
                }
                notificationDAO.insert(customerAccountId, bookingId, "Đã hủy chuyến", msg, "BOOKING_CANCELLED");
            }
        } catch (Exception notifyEx) {
            System.err.println("Notify cancelBooking (customer) loi: " + notifyEx.getMessage());
        }

        // Báo cho driver đã được gán chuyến (nếu có) — không để lỗi notify làm fail API hủy
        try {
            int driverAccountId = notificationDAO.resolveDriverAccountByBookingId(bookingId);
            if (driverAccountId != -1) {
                notificationDAO.insert(driverAccountId, bookingId,
                        "Chuyến đã bị khách hủy",
                        "Chuyến #" + bookingId + " đã bị khách hàng hủy. Bạn không cần thực hiện chuyến này nữa.",
                        "BOOKING_CANCELLED");
            }
        } catch (Exception notifyEx) {
            System.err.println("Notify cancelBooking (driver) loi: " + notifyEx.getMessage());
        }

        // Báo cho các dispatcher đang hoạt động để nắm tình hình điều phối
        try {
            for (int dispatcherAccountId : notificationDAO.getDispatcherAccountIds()) {
                notificationDAO.insert(dispatcherAccountId, bookingId,
                        "Booking #" + bookingId + " đã bị hủy",
                        "Khách hàng đã hủy chuyến #" + bookingId
                        + (isForfeitDeposit
                                ? " (mất cọc " + penaltyAmount.toPlainString() + " đ)."
                                : " (không mất phí)."),
                        "BOOKING_CANCELLED");
            }
        } catch (Exception notifyEx) {
            System.err.println("Notify cancelBooking (dispatcher) loi: " + notifyEx.getMessage());
        }

        return new CancelResult(
                bookingId,
                isForfeitDeposit,
                penaltyAmount
        );
    }

    public static class CancelResult {

        public final int bookingId;
        public final boolean forfeitDeposit;
        public final BigDecimal penaltyAmount;

        public CancelResult(int bookingId, boolean forfeitDeposit, BigDecimal penaltyAmount) {
            this.bookingId = bookingId;
            this.forfeitDeposit = forfeitDeposit;
            this.penaltyAmount = penaltyAmount;
        }
    }

    // ===================== BE-26: Tính giá ước tính =====================
    //
    // ROUND_TRIP + DISTANCE: tổng = PricePerKm × (distanceKm + returnDistanceKm)
    // ONE_WAY  + DISTANCE  : tổng = PricePerKm × distanceKm
    // HOURLY               : tổng = PricePerHour × durationHours
    // DAILY                : tổng = PricePerDay  × durationDays
    //
    public PriceResult checkPrice(int vehicleId, String bookingType, String tripDirection,
            double distanceKm, double returnDistanceKm,
            int durationHours, int durationDays,
            Timestamp departureTime) throws Exception {

        PricingRule rule = dao.getPricingRule(vehicleId, bookingType, tripDirection);
        if (rule == null) {
            throw new IllegalArgumentException("Không tìm thấy bảng giá cho xe và loại booking này");
        }

        boolean isRoundTrip = "ROUND_TRIP".equalsIgnoreCase(tripDirection);

        BigDecimal base = rule.getBasePrice() != null ? rule.getBasePrice() : BigDecimal.ZERO;
        BigDecimal fare = BigDecimal.ZERO;

        switch (bookingType.toUpperCase()) {
            case "DISTANCE":
            case "INNER_CITY":
            case "INTER_CITY":
                if (rule.getPricePerKm() != null) {
                    double totalKm = isRoundTrip ? (distanceKm + returnDistanceKm) : distanceKm;
                    fare = rule.getPricePerKm().multiply(BigDecimal.valueOf(totalKm));
                }
                break;
            case "HOURLY":
                if (rule.getPricePerHour() != null) {
                    fare = rule.getPricePerHour().multiply(BigDecimal.valueOf(durationHours));
                }
                break;
            case "DAILY":
                if (rule.getPricePerDay() != null) {
                    fare = rule.getPricePerDay().multiply(BigDecimal.valueOf(durationDays));
                }
                break;
        }

        BigDecimal baseFare = base.add(fare);

        // Check weekend surcharge
        BigDecimal weekendSurcharge = BigDecimal.ZERO;
        if (departureTime != null && rule.getWeekendMultiplier() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(departureTime);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                weekendSurcharge = baseFare.multiply(
                        rule.getWeekendMultiplier().subtract(BigDecimal.ONE)
                ).setScale(0, RoundingMode.HALF_UP);
            }
        }

        BigDecimal estimatedTotal = baseFare.add(weekendSurcharge);
        BigDecimal deposit = estimatedTotal.multiply(DEPOSIT_RATE)
                .setScale(0, RoundingMode.HALF_UP);

        return new PriceResult(rule.getId(), baseFare, weekendSurcharge, estimatedTotal, deposit,
                distanceKm, returnDistanceKm);
    }

    public static class PriceResult {

        public final int ruleId;
        public final BigDecimal baseFare;
        public final BigDecimal weekendSurcharge;
        public final BigDecimal estimatedTotal;
        public final BigDecimal deposit30Percent;
        // --- breakdown khoảng cách để frontend hiển thị ---
        public final double legDistanceKm;      // chiều đi
        public final double returnDistanceKm;   // chiều về (0 nếu ONE_WAY)

        public PriceResult(int ruleId, BigDecimal baseFare, BigDecimal weekendSurcharge,
                BigDecimal estimatedTotal, BigDecimal deposit30Percent,
                double legDistanceKm, double returnDistanceKm) {
            this.ruleId = ruleId;
            this.baseFare = baseFare;
            this.weekendSurcharge = weekendSurcharge;
            this.estimatedTotal = estimatedTotal;
            this.deposit30Percent = deposit30Percent;
            this.legDistanceKm = legDistanceKm;
            this.returnDistanceKm = returnDistanceKm;
        }
    }

    // ===================== BE-27: Apply Voucher =====================
    public VoucherResult applyVoucher(String code, int customerId,
            BigDecimal estimatedTotal, int vehicleTypeId) throws Exception {

        Voucher voucher = dao.findVoucherByCode(code);
        if (voucher == null) {
            throw new IllegalArgumentException("Mã voucher không hợp lệ hoặc đã hết hạn");
        }

        // Check min booking value
        if (voucher.getMinBookingValue() != null
                && estimatedTotal.compareTo(voucher.getMinBookingValue()) < 0) {
            throw new IllegalArgumentException(
                    "Đơn hàng tối thiểu " + voucher.getMinBookingValue() + "đ để dùng voucher này");
        }

        // Check applicable vehicle type
        if (voucher.getApplicableVehicleTypeId() != 0
                && voucher.getApplicableVehicleTypeId() != vehicleTypeId) {
            throw new IllegalArgumentException("Voucher không áp dụng cho loại xe này");
        }

        // Check max usage per user
        if (voucher.getMaxUsagePerUser() != null) {
            int used = dao.countVoucherUsageByCustomer(voucher.getId(), customerId);
            System.out.println("DEBUG voucherId=" + voucher.getId() + " customerId=" + customerId + " used=" + used + " max=" + voucher.getMaxUsagePerUser());
            if (used >= voucher.getMaxUsagePerUser()) {
                throw new IllegalArgumentException("Bạn đã dùng voucher này đủ số lần tối đa");
            }
        }

        // Tính discount
        BigDecimal discount;
        if ("PERCENT".equals(voucher.getDiscountType())) {
            discount = estimatedTotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null
                    && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            // FIXED
            discount = voucher.getDiscountValue();
        }

        BigDecimal finalTotal = estimatedTotal.subtract(discount).max(BigDecimal.ZERO);

        return new VoucherResult(voucher.getId(), voucher.getCode(),
                discount, finalTotal);
    }

    public static class VoucherResult {

        public final int voucherId;
        public final String code;
        public final BigDecimal discountAmount;
        public final BigDecimal finalTotal;

        public VoucherResult(int voucherId, String code,
                BigDecimal discountAmount, BigDecimal finalTotal) {
            this.voucherId = voucherId;
            this.code = code;
            this.discountAmount = discountAmount;
            this.finalTotal = finalTotal;
        }
    }
}