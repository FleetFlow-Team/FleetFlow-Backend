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
    // Hủy trước >= 12h  → FREE, không mất gì
    // Hủy trước < 12h   → MẤT CỌC (mất nguyên 30% tiền cọc đã đặt)
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
                // BR-12 (mới): >=12h trước giờ khởi hành → free; <12h → mất cọc
                isForfeitDeposit = hoursUntilDeparture < 12;
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

            // Message gửi cho CHÍNH khách hàng đó — xưng "Bạn" là đúng vì người
            // nhận notification chính là người bị mất cọc.
            String penaltyMsgForCustomer;
            if (isForfeitDeposit) {
                penaltyMsgForCustomer = " Bạn bị mất cọc " + penaltyAmount.toPlainString() + "đ do hủy trong vòng 12h.";
            } else if (refunded.compareTo(BigDecimal.ZERO) > 0) {
                penaltyMsgForCustomer = " Không mất phí hủy. Cọc " + refunded.toPlainString() + "đ đã được hoàn lại vào ví của bạn.";
            } else {
                penaltyMsgForCustomer = " Không mất phí hủy.";
            }

            // Message gửi cho dispatcher/driver — KHÔNG được xưng "Bạn" vì người
            // nhận không phải là khách hàng, phải nêu rõ tên khách bị mất cọc.
            String customerName = extDAO.getCustomerNameByBookingId(bookingId);
            String customerLabel = (customerName != null && !customerName.isEmpty())
                    ? customerName : ("Khách hàng #" + customerId);
            String penaltyMsgForStaff;
            if (isForfeitDeposit) {
                penaltyMsgForStaff = " " + customerLabel + " bị mất cọc " + penaltyAmount.toPlainString() + "đ do hủy trong vòng 12h.";
            } else if (refunded.compareTo(BigDecimal.ZERO) > 0) {
                penaltyMsgForStaff = " Không mất phí hủy. Cọc " + refunded.toPlainString() + "đ đã được hoàn lại vào ví của khách.";
            } else {
                penaltyMsgForStaff = " Không mất phí hủy.";
            }

            int customerAccountId = extDAO.getCustomerAccountIdByBookingId(bookingId);
            if (customerAccountId != -1) {
                extDAO.createNotification(customerAccountId, bookingId,
                        "Booking #" + bookingId + " đã bị hủy",
                        "Chuyến đi của bạn đã được hủy thành công." + penaltyMsgForCustomer,
                        "BOOKING_CANCELLED", "IN_APP");
            }

            int driverAccountId = extDAO.getDriverAccountIdByBookingId(bookingId);
            if (driverAccountId != -1) {
                extDAO.createNotification(driverAccountId, bookingId,
                        "Chuyến đi #" + bookingId + " bị hủy",
                        customerLabel + " đã hủy booking #" + bookingId
                                + (reason != null && !reason.isEmpty() ? ". Lý do: " + reason : "."),
                        "BOOKING_CANCELLED", "IN_APP");
            }

            java.util.List<Integer> dispatcherIds = new dao.AccountDAO().getActiveDispatcherAccountIds();
            for (int dispId : dispatcherIds) {
                extDAO.createNotification(dispId, bookingId,
                        "Booking #" + bookingId + " bị hủy bởi khách",
                        customerLabel + " đã hủy booking #" + bookingId + "." + penaltyMsgForStaff,
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
        // BigDecimal deposit = estimatedTotal.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.HALF_UP); // code cũ
        BigDecimal deposit = PaymentService.depositAmountOf(estimatedTotal);

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