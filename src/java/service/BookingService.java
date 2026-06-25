package service;

import dao.BookingDAO;
import dao.CustomerBookingDAO;
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.Booking;
import model.Voucher;
import model.BookingDetail;
import model.BookingPricing;

public class BookingService {

    private final BookingDAO bookingDAO = new BookingDAO();
    private final MapsService mapsService = new MapsService();
    private final CustomerBookingDAO customerBookingDAO
            = new CustomerBookingDAO();

    /**
     * Tạo booking mới — full flow: 1. Validate khoảng cách >= 20km (BR-01) —
     * CHỈ áp dụng cho bookingType = DISTANCE 2. Validate thời gian đặt trước >=
     * 120 phút (BR-02) 3. Validate xe AVAILABLE (BR-22) 4. Validate không trùng
     * lịch xe (BR-27) 5. Validate ROUND_TRIP phải có returnTime 6. Insert
     * Booking + BookingDetail vào DB
     *
     * @param durationHours Số giờ thuê — chỉ áp dụng khi bookingType = HOURLY
     * @param durationDays Số ngày thuê — chỉ áp dụng khi bookingType = DAILY
     */
    public long createBooking(
            int customerId,
            int vehicleId,
            Integer voucherId,
            String bookingType,
            String tripDirection,
            String pickupAddress,
            Double pickupLat,
            Double pickupLng,
            String dropoffAddress,
            Double dropoffLat,
            Double dropoffLng,
            Timestamp departureTime,
            Timestamp returnTime,
            Integer durationHours,
            Integer durationDays,
            // --- Chiều về (bắt buộc khi ROUND_TRIP + DISTANCE) ---
            String returnPickupAddress,
            Double returnPickupLat,
            Double returnPickupLng,
            String returnDropoffAddress,
            Double returnDropoffLat,
            Double returnDropoffLng
    ) throws Exception {

        boolean isDistanceType = "DISTANCE".equalsIgnoreCase(bookingType);
        boolean isHourly = "HOURLY".equalsIgnoreCase(bookingType);
        boolean isDaily = "DAILY".equalsIgnoreCase(bookingType);
        boolean isRoundTrip = "ROUND_TRIP".equalsIgnoreCase(tripDirection);

        // ---- Vấn đề 2 fix: chỉ gọi Maps API + validate 20km khi là DISTANCE ----
        double distanceKm = 0;
        double returnDistanceKm = 0;
        if (isDistanceType) {
            if (pickupLat == null || pickupLng == null || dropoffLat == null || dropoffLng == null) {
                throw new IllegalArgumentException(
                        "Thiếu tọa độ điểm đón/trả — bắt buộc với loại đặt xe theo quãng đường."
                );
            }
            // BR-01: Validate khoảng cách tối thiểu 20km (chiều đi)
            distanceKm = mapsService.validateAndGetDistance(
                    pickupLat, pickupLng,
                    dropoffLat, dropoffLng
            );

            // ROUND_TRIP: validate + tính khoảng cách chiều về
            if (isRoundTrip) {
                if (returnPickupLat == null || returnPickupLng == null
                        || returnDropoffLat == null || returnDropoffLng == null) {
                    throw new IllegalArgumentException(
                            "Đặt xe 2 chiều bắt buộc phải cung cấp tọa độ điểm đón/trả chiều về."
                    );
                }
                // Chiều về cũng phải >= 20km (BR-01 áp dụng từng chặng)
                returnDistanceKm = mapsService.validateAndGetDistance(
                        returnPickupLat, returnPickupLng,
                        returnDropoffLat, returnDropoffLng
                );
            }
        }

        // ---- Vấn đề 1 fix: validate duration bắt buộc theo loại booking ----
        if (isHourly && (durationHours == null || durationHours <= 0)) {
            throw new IllegalArgumentException(
                    "Thiếu durationHours hoặc không hợp lệ — bắt buộc với loại đặt xe theo giờ."
            );
        }
        if (isDaily && (durationDays == null || durationDays <= 0)) {
            throw new IllegalArgumentException(
                    "Thiếu durationDays hoặc không hợp lệ — bắt buộc với loại đặt xe theo ngày."
            );
        }

        // ---- BR-02: Validate thời gian đặt trước tối thiểu 120 phút ----
        long now = System.currentTimeMillis();
        long diffMinutes = (departureTime.getTime() - now) / (1000 * 60);
        if (diffMinutes < 120) {
            throw new IllegalArgumentException(
                    "Phải đặt xe trước giờ khởi hành tối thiểu 120 phút. "
                    + "Hiện tại chỉ còn " + diffMinutes + " phút."
            );
        }

        // ---- Vấn đề 3 fix: ROUND_TRIP bắt buộc phải có returnTime và sau departureTime ----
        if (isRoundTrip) {
            if (returnTime == null) {
                throw new IllegalArgumentException(
                        "Đặt xe 2 chiều (ROUND_TRIP) bắt buộc phải có returnTime."
                );
            }
            if (!returnTime.after(departureTime)) {
                throw new IllegalArgumentException(
                        "returnTime phải sau departureTime."
                );
            }
        }

        // ---- BR-22: Validate xe có AVAILABLE không ----
        boolean isAvailable = bookingDAO.isVehicleAvailable(vehicleId);
        if (!isAvailable) {
            throw new IllegalArgumentException(
                    "Xe này hiện không sẵn sàng (đang bảo dưỡng hoặc không hoạt động)."
            );
        }

        // ---- BR-27: Validate không trùng lịch ----
        boolean hasConflict = bookingDAO.isVehicleScheduleConflict(vehicleId, departureTime);
        if (hasConflict) {
            throw new IllegalArgumentException(
                    "Xe này đã có lịch chạy gần giờ bạn chọn. "
                    + "Vui lòng chọn thời gian khác hoặc xe khác (cần cách chuyến cũ ít nhất 60 phút)."
            );
        }

        // ---- Tạo Booking object ----
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setVehicleId(vehicleId);
        if (voucherId != null) {
            booking.setVoucherId(voucherId);
        }
        booking.setBookingType(bookingType);
        booking.setTripDirection(tripDirection);
        booking.setStatus("PENDING");
        booking.setCreatedAt(new Timestamp(now));

        // ---- Tạo BookingDetail object ----
        BookingDetail detail = new BookingDetail();
        detail.setPickupAddress(pickupAddress);
        detail.setDropoffAddress(dropoffAddress);
        detail.setDepartureTime(departureTime);
        detail.setReturnTime(returnTime);

        if (isDistanceType) {
            // Quãng đường: lưu tọa độ + km thật từ Maps API
            detail.setPickupLat(BigDecimal.valueOf(pickupLat));
            detail.setPickupLng(BigDecimal.valueOf(pickupLng));
            detail.setDropoffLat(BigDecimal.valueOf(dropoffLat));
            detail.setDropoffLng(BigDecimal.valueOf(dropoffLng));
            detail.setDistanceKm(BigDecimal.valueOf(distanceKm));

            // ROUND_TRIP: lưu thêm dữ liệu chiều về
            if (isRoundTrip) {
                detail.setReturnPickupAddress(returnPickupAddress);
                detail.setReturnPickupLat(BigDecimal.valueOf(returnPickupLat));
                detail.setReturnPickupLng(BigDecimal.valueOf(returnPickupLng));
                detail.setReturnDropoffAddress(returnDropoffAddress);
                detail.setReturnDropoffLat(BigDecimal.valueOf(returnDropoffLat));
                detail.setReturnDropoffLng(BigDecimal.valueOf(returnDropoffLng));
                detail.setReturnDistanceKm(BigDecimal.valueOf(returnDistanceKm));
            }
        } else if (isHourly) {
            // Theo giờ: không cần tọa độ, lưu số giờ thuê vào cột DurationHours riêng
            detail.setDurationHours(durationHours);
        } else if (isDaily) {
            // Theo ngày: lưu số ngày thuê vào cột DurationDays riêng
            detail.setDurationDays(durationDays);
        }

        // ---- Insert vào DB ----
        CustomerBookingService pricingService
                = new CustomerBookingService();
        CustomerBookingService.PriceResult price
                = pricingService.checkPrice(
                        vehicleId,
                        bookingType,
                        tripDirection,
                        distanceKm,
                        returnDistanceKm,
                        durationHours != null ? durationHours : 0,
                        durationDays != null ? durationDays : 0,
                        departureTime
                );
        BookingPricing pricing = new BookingPricing();

        pricing.setRuleId(price.ruleId);
        pricing.setBaseFare(price.baseFare);
        pricing.setWeekendSurcharge(price.weekendSurcharge);
        BigDecimal discountAmount
                = BigDecimal.ZERO;

        BigDecimal finalTotal
                = price.estimatedTotal;
        if (voucherId != null) {

            Voucher voucher
                    = customerBookingDAO.findVoucherById(
                            voucherId
                    );

            int vehicleTypeId
                    = customerBookingDAO.getVehicleTypeId(
                            vehicleId
                    );

            CustomerBookingService.VoucherResult vr
                    = pricingService.applyVoucher(
                            voucher.getCode(),
                            customerId,
                            price.estimatedTotal,
                            vehicleTypeId
                    );

            discountAmount
                    = vr.discountAmount;

            finalTotal
                    = vr.finalTotal;
        }
        pricing.setDiscountAmount(
                discountAmount
        );

        pricing.setEstimatedTotal(
                finalTotal
        );
        return bookingDAO.createBooking(booking, detail, pricing);
    }

    /**
     * Geocode địa chỉ text → tọa độ
     */
    public double[] geocodeAddress(String address) throws Exception {
        return mapsService.geocode(address);
    }

    /**
     * Lấy thông tin booking theo ID
     */
    public Booking getBookingById(int bookingId) throws Exception {
        return bookingDAO.findById(bookingId);
    }

    /**
     * Lấy BookingDetail theo BookingID
     */
    public BookingDetail getBookingDetail(int bookingId) throws Exception {
        return bookingDAO.findDetailByBookingId(bookingId);
    }

    /**
     * Lấy BookingPricing theo BookingID (BaseFare, WeekendSurcharge,
     * DiscountAmount, EstimatedTotal — giá đã áp voucher nếu có)
     */
    public BookingPricing getBookingPricing(int bookingId) throws Exception {
        return bookingDAO.findPricingByBookingId(bookingId);
    }

    /**
     * Cập nhật trạng thái booking
     */
    public void updateBookingStatus(int bookingId, String status) throws Exception {
        bookingDAO.updateStatus(bookingId, status);
    }
}