package service;

import model.Booking;
import dao.CustomerBookingDAO;
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

    // ===================== BE-23: Lịch sử đặt xe =====================
    public List<BookingRow> getBookingHistory(int customerId) throws Exception {
        return dao.getBookingsByCustomerId(customerId);
    }

    // ===================== BE-25: Cancel + tính phạt =====================
    // Logic phạt (đã đổi theo yêu cầu mới — KHÔNG còn ghi nợ vào ví,
    // KHÔNG còn tiered 30%/50% theo % tổng tiền):
    // Hủy trước >= 6h  → FREE, không mất gì (đã hạ từ 12h xuống 6h, xem lý do ở BR-12 bên dưới)
    // Hủy trước < 6h   → MẤT CỌC (mất nguyên 30% tiền cọc đã đặt)
    // CustomerWalletLedger từ giờ CHỈ dùng để Admin ghi nhận hoàn tiền (REFUND),
    // không còn dùng để ghi công nợ phạt nữa.
    // Booking đang COMPLETED/CANCELLED → không cho hủy
    public CancelResult cancelBooking(int bookingId, int customerId, String reason, String ipAddress) throws Exception {
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

        // Grace period: hủy trong vòng 10 phút sau khi tạo booking thì luôn miễn phí,
        // không phụ thuộc còn bao lâu tới giờ khởi hành — tránh xung đột với rule đặt xe
        // tối thiểu trước 120 phút (BR-02), vì user có thể bấm nhầm ngay sau khi đặt.
        boolean isForfeitDeposit = false;
        if (departureTime != null) {
            long now = System.currentTimeMillis();
            long minutesSinceCreated = (now - booking.getCreatedAt().getTime()) / (1000 * 60);

            if (minutesSinceCreated > 10) {
                long hoursUntilDeparture = (departureTime.getTime() - now) / (1000 * 60 * 60);
                // BR-12 (mới, hạ từ 12h xuống 6h): >=6h trước giờ khởi hành -> free; <6h -> mất cọc.
                // Lý do hạ xuống 6h: BR-02 (đặt trước tối thiểu) vừa tách theo loại —
                // DAILY/DISTANCE giờ bắt buộc đặt trước >=12h. Nếu BR-12 vẫn giữ 12h thì
                // NGAY LÚC VỪA ĐẶT XONG khách đã rơi đúng vào ngưỡng mất cọc (12h đặt trước
                // == 12h ngưỡng hủy free, sai 1 phút là dính), coi như không có cửa hủy free
                // thật sự. Hạ xuống 6h tạo ra 1 khoảng đệm 6 tiếng (12h - 6h) sau khi đặt để
                // khách còn có thời gian đổi ý miễn phí, trước khi rơi vào vùng mất cọc.
                // LƯU Ý: HOURLY vẫn đặt trước tối thiểu chỉ 2h (BR-02), nên với HOURLY, ngưỡng
                // 6h này vẫn LỚN HƠN thời gian đặt trước tối thiểu -> HOURLY vẫn gần như không
                // có cửa hủy free thật (ngoài đúng 10 phút grace phía trên) — đây là vấn đề
                // CÒN TỒN ĐỌNG, chưa xử lý trong lần sửa này, cần bàn riêng sau.
                isForfeitDeposit = hoursUntilDeparture < 6;
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

        // BigDecimal depositAmount = totalAmount.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP); // code cũ
        BigDecimal depositAmount = PaymentService.depositAmountOf(totalAmount);

        BigDecimal penaltyAmount = isForfeitDeposit ? depositAmount : BigDecimal.ZERO;

        int customerAccountIdForLog = new dao.CustomerLockDAO().getAccountIdByCustomerId(customerId);

        BigDecimal refunded = dao.cancelBookingWithPenalty(
                bookingId,
                customerId,
                isForfeitDeposit,
                penaltyAmount,
                reason,
                customerAccountIdForLog,
                ipAddress,
                booking.getStatus()
        );

        // Hủy broadcast PENDING — driver không thể start/complete chuyến đã bị cancel
        try {
            new dao.DriverJobBroadcastDAO().cancelPendingBroadcastsByBookingId(bookingId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Notify customer, driver, dispatcher khi hủy chuyến
        try {
            dao.ExtensionDAO extDAO = new dao.ExtensionDAO();
            String penaltyMsg;
            if (isForfeitDeposit) {
                penaltyMsg = " Bạn bị mất cọc " + penaltyAmount.toPlainString() + "đ do hủy trong vòng 6h.";
            } else if (refunded.compareTo(BigDecimal.ZERO) > 0) {
                penaltyMsg = " Không mất phí hủy. Cọc " + refunded.toPlainString() + "đ đã được hoàn lại vào ví của bạn.";
            } else {
                penaltyMsg = " Không mất phí hủy.";
            }

            int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                extDAO.createNotification(customerAccountId, bookingId,
                        "Booking #" + bookingId + " đã bị hủy",
                        "Chuyến đi của bạn đã được hủy thành công." + penaltyMsg,
                        "BOOKING_CANCELLED", "IN_APP");
            }

            int driverAccountId = extDAO.getDriverAccountIdByBookingId(bookingId);
            if (driverAccountId != -1) {
                extDAO.createNotification(driverAccountId, bookingId,
                        "Chuyến đi #" + bookingId + " bị hủy",
                        "Khách hàng đã hủy booking #" + bookingId
                                + (reason != null && !reason.isEmpty() ? ". Lý do: " + reason : "."),
                        "BOOKING_CANCELLED", "IN_APP");
            }

            java.util.List<Integer> dispatcherIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
            for (int dispId : dispatcherIds) {
                extDAO.createNotification(dispId, bookingId,
                        "Booking #" + bookingId + " bị hủy bởi khách",
                        "Khách hàng đã hủy booking #" + bookingId + "." + penaltyMsg,
                        "BOOKING_CANCELLED", "IN_APP");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new CancelResult(
                bookingId,
                isForfeitDeposit,
                penaltyAmount,
                refunded
        );
    }

    public static class CancelResult {

        public final int bookingId;
        public final boolean forfeitDeposit;
        public final BigDecimal penaltyAmount;
        public final BigDecimal refundedAmount;

        public CancelResult(int bookingId, boolean forfeitDeposit, BigDecimal penaltyAmount, BigDecimal refundedAmount) {
            this.bookingId = bookingId;
            this.forfeitDeposit = forfeitDeposit;
            this.penaltyAmount = penaltyAmount;
            this.refundedAmount = refundedAmount;
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

        // Check weekend/holiday surcharge — dùng chung WeekendMultiplier của PricingRule.
        // Một ngày vừa là cuối tuần vừa là ngày lễ chỉ tính phụ phí 1 lần (không cộng dồn).
        BigDecimal weekendSurcharge = BigDecimal.ZERO;
        BigDecimal holidaySurcharge = BigDecimal.ZERO;
        if (departureTime != null && rule.getWeekendMultiplier() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(departureTime);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            boolean isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
            BigDecimal surcharge = baseFare.multiply(
                    rule.getWeekendMultiplier().subtract(BigDecimal.ONE)
            ).setScale(0, RoundingMode.HALF_UP);
            if (isWeekend) {
                weekendSurcharge = surcharge;
            } else if (new dao.HolidayDAO().isHoliday(new java.sql.Date(departureTime.getTime()))) {
                holidaySurcharge = surcharge;
            }
        }

        BigDecimal estimatedTotal = baseFare.add(weekendSurcharge).add(holidaySurcharge);

        // BigDecimal deposit = estimatedTotal.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP); // code cũ
        BigDecimal deposit = PaymentService.depositAmountOf(estimatedTotal);

        return new PriceResult(rule.getId(), baseFare, weekendSurcharge, holidaySurcharge, estimatedTotal, deposit,
                distanceKm, returnDistanceKm);
    }

    public static class PriceResult {

        public final int ruleId;
        public final BigDecimal baseFare;
        public final BigDecimal weekendSurcharge;
        public final BigDecimal holidaySurcharge;
        public final BigDecimal estimatedTotal;
        public final BigDecimal deposit30Percent;
        // --- breakdown khoảng cách để frontend hiển thị ---
        public final double legDistanceKm;      // chiều đi
        public final double returnDistanceKm;   // chiều về (0 nếu ONE_WAY)

        public PriceResult(int ruleId, BigDecimal baseFare, BigDecimal weekendSurcharge,
                BigDecimal holidaySurcharge, BigDecimal estimatedTotal, BigDecimal deposit30Percent,
                double legDistanceKm, double returnDistanceKm) {
            this.ruleId = ruleId;
            this.baseFare = baseFare;
            this.weekendSurcharge = weekendSurcharge;
            this.holidaySurcharge = holidaySurcharge;
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

        // Check tổng số lượng voucher available (toàn hệ thống, không phân biệt khách hàng)
        if (voucher.getTotalQuantity() != null) {
            int totalUsed = dao.countVoucherUsageTotal(voucher.getId());
            if (totalUsed >= voucher.getTotalQuantity()) {
                throw new IllegalArgumentException("Voucher đã hết lượt sử dụng");
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