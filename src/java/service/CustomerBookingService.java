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
import service.CustomerLockService;

public class CustomerBookingService {

    private final CustomerBookingDAO dao = new CustomerBookingDAO();
    private final CustomerLockService customerLockService = new CustomerLockService();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    // ===================== BE-23: Lịch sử đặt xe =====================
    public List<BookingRow> getBookingHistory(int customerId) throws Exception {
        return dao.getBookingsByCustomerId(customerId);
    }

    // ===================== BE-25: Cancel + tính phạt =====================
    // Logic phạt:
    // Hủy trước >= 24h  → không phạt (0%)
    // Hủy trước 12-24h  → phạt 30%
    // Hủy trước < 12h   → phạt 50%
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
        int penaltyPercent = 0;
        if (departureTime != null) {
            long now = System.currentTimeMillis();
            long minutesSinceCreated = (now - booking.getCreatedAt().getTime()) / (1000 * 60);

            if (minutesSinceCreated <= 10) {
                penaltyPercent = 0;
            } else {
                long hoursUntilDeparture = (departureTime.getTime() - now) / (1000 * 60 * 60);
                if (hoursUntilDeparture >= 12) {       // BR-12: >=12h không phạt
                    penaltyPercent = 0;
                } else if (hoursUntilDeparture >= 6) { // BR-12: 6-12h phạt 30%
                    penaltyPercent = 30;
                } else {                                // BR-12: <6h phạt 50%
                    penaltyPercent = 50;
                }
            }
        }

        // Tính tiền phạt
// Lấy giá booking hiện tại
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            totalAmount = dao.getBookingTotalAmount(bookingId);
        } catch (Exception e) {
            System.err.println("Không lấy được giá booking: " + e.getMessage());
        }

// penalty = tổng tiền * %
        BigDecimal penaltyAmount = totalAmount
                .multiply(BigDecimal.valueOf(penaltyPercent))
                .divide(BigDecimal.valueOf(100));

        dao.cancelBookingWithPenalty(
                bookingId,
                customerId,
                penaltyPercent,
                penaltyAmount,
                reason
        );

// Sau khi ghi nhận tiền phạt vào CustomerWalletLedger,
// kiểm tra công nợ có vượt ngưỡng cảnh báo hay không
        try {
            customerLockService.checkAndWarnIfDebtExceeded(
                    customerId,
                    1 // AdminID mặc định
            );
        } catch (Exception e) {
            // Không để lỗi cảnh báo làm fail API hủy booking
            System.err.println(
                    "Lỗi khi kiểm tra cảnh báo nợ: "
                    + e.getMessage()
            );
        }

        try {
            int customerAccountId = notificationDAO.resolveCustomerAccountByCustomerId(customerId);
            if (customerAccountId != -1) {
                String msg = "Chuyến #" + bookingId + " đã được hủy thành công.";
                if (penaltyPercent > 0) {
                    msg += " Phí phạt hủy: " + penaltyAmount.setScale(0, RoundingMode.HALF_UP).toPlainString()
                            + " đ (" + penaltyPercent + "%).";
                } else {
                    msg += " Bạn không bị tính phí phạt cho lần hủy này.";
                }
                notificationDAO.insert(customerAccountId, bookingId, "Đã hủy chuyến", msg, "BOOKING_CANCELLED");
            }
        } catch (Exception notifyEx) {
            System.err.println("Notify cancelBooking loi: " + notifyEx.getMessage());
        }

        return new CancelResult(
                bookingId,
                penaltyPercent,
                penaltyAmount
        );
    }

    public static class CancelResult {

        public final int bookingId;
        public final int penaltyPercent;
        public final BigDecimal penaltyAmount;
        public final boolean forfeitDeposit;

        public CancelResult(int bookingId, int penaltyPercent, BigDecimal penaltyAmount) {
            this.bookingId = bookingId;
            this.penaltyPercent = penaltyPercent;
            this.penaltyAmount = penaltyAmount;
            this.forfeitDeposit = penaltyPercent > 0;
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
        BigDecimal deposit = estimatedTotal.multiply(new BigDecimal("0.30"))
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