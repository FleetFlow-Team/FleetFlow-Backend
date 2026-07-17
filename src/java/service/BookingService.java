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
        boolean isInnerCity = "INNER_CITY".equalsIgnoreCase(bookingType);
        boolean isInterCity = "INTER_CITY".equalsIgnoreCase(bookingType);
        // Bug cũ: chỉ check đúng chữ "DISTANCE" nên INNER_CITY/INTER_CITY bị bỏ qua
        // hoàn toàn bước tính khoảng cách -> distanceKm luôn = 0 -> tiền tính sai.
        // Cả 3 loại đều cần tính khoảng cách thật, chỉ khác ngưỡng tối thiểu.
        boolean needsDistance = isDistanceType || isInnerCity || isInterCity;
        boolean isHourly = "HOURLY".equalsIgnoreCase(bookingType);
        boolean isDaily = "DAILY".equalsIgnoreCase(bookingType);
        boolean isRoundTrip = "ROUND_TRIP".equalsIgnoreCase(tripDirection);

        // BR-01: DISTANCE giữ nguyên tối thiểu 20km (chuyến khách tự chọn điểm tự do).
        // INNER_CITY/INTER_CITY là tuyến đến điểm cố định (sân bay, bến xe...) -> ưu ái 10km.
        double minDistanceKm = isDistanceType ? 20.0 : 10.0;

        double distanceKm = 0;
        double returnDistanceKm = 0;
        if (needsDistance) {
            if (pickupLat == null || pickupLng == null || dropoffLat == null || dropoffLng == null) {
                throw new IllegalArgumentException(
                        "Thiếu tọa độ điểm đón/trả — bắt buộc với loại đặt xe theo quãng đường."
                );
            }
            distanceKm = mapsService.validateAndGetDistance(
                    pickupLat, pickupLng,
                    dropoffLat, dropoffLng,
                    minDistanceKm
            );

            // ROUND_TRIP: validate + tính khoảng cách chiều về
            if (isRoundTrip) {
                if (returnPickupLat == null || returnPickupLng == null
                        || returnDropoffLat == null || returnDropoffLng == null) {
                    throw new IllegalArgumentException(
                            "Đặt xe 2 chiều bắt buộc phải cung cấp tọa độ điểm đón/trả chiều về."
                    );
                }
                // Chiều về áp cùng ngưỡng tối thiểu với chiều đi
                returnDistanceKm = mapsService.validateAndGetDistance(
                        returnPickupLat, returnPickupLng,
                        returnDropoffLat, returnDropoffLng,
                        minDistanceKm
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

        // ---- BR-02: Validate thời gian đặt trước tối thiểu — tách theo loại booking ----
        // HOURLY: nhu cầu thường gấp (đi công việc đột xuất) -> giữ ngắn 120 phút.
        // DAILY/DISTANCE (kể cả INNER_CITY/INTER_CITY, đi xa/dùng lâu, cần chuẩn bị xe +
        // tài xế nhiều hơn) -> nâng lên 12 tiếng. Số này đồng thời phải ăn khớp với BR-12
        // (ngưỡng hủy mất cọc ở CustomerBookingService) để khách còn có cửa hủy miễn phí
        // thật sự sau khi đặt — xem thêm comment ở BR-12.
        long now = System.currentTimeMillis();
        long diffMinutes = (departureTime.getTime() - now) / (1000 * 60);
        long minAdvanceMinutes = isHourly ? 120 : 720; // DAILY/DISTANCE/INNER_CITY/INTER_CITY = 12h
        if (diffMinutes < minAdvanceMinutes) {
            throw new IllegalArgumentException(
                    "Phải đặt xe trước giờ khởi hành tối thiểu " + minAdvanceMinutes + " phút. "
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

        // ---- BR-22: Validate xe có AVAILABLE không (check sớm để báo lỗi nhanh cho UX) ----
        // LƯU Ý: đây chỉ là check "báo trước", KHÔNG có khóa gì cả nên vẫn có khoảng hở
        // race condition nếu 2 request đến gần như cùng lúc. Tuyến phòng thủ THẬT SỰ nằm
        // trong BookingDAO.createBooking — nơi khóa dòng Vehicle (UPDLOCK, HOLDLOCK) rồi
        // re-check lại trong cùng transaction trước khi insert. Check ở đây chỉ giúp trả
        // lỗi nhanh, thân thiện hơn cho trường hợp thường (xe rõ ràng đang bận), không phải
        // chỗ đảm bảo tính đúng đắn.
        boolean isAvailable = bookingDAO.isVehicleAvailable(vehicleId);
        if (!isAvailable) {
            throw new IllegalArgumentException(
                    "Xe này hiện không sẵn sàng (đang bảo dưỡng hoặc không hoạt động)."
            );
        }

        // ---- Tự tính giờ kết thúc thực tế của chuyến (effectiveEndTime) ----
        // Trước đây HOURLY/DAILY không lưu ReturnTime -> DAO phải đoán bừa "8 tiếng"
        // khi check trùng lịch, khiến xe bị khóa dư ra dù chỉ thuê 1-2 tiếng.
        // Giờ tính rõ ràng theo từng loại rồi lưu luôn vào ReturnTime để dùng chung
        // cho mọi lần check lịch sau này (kể cả của booking khác tham chiếu tới).
        Timestamp effectiveEndTime = returnTime; // đã có sẵn nếu ROUND_TRIP có gửi lên
        if (effectiveEndTime == null) {
            if (isHourly) {
                effectiveEndTime = new Timestamp(departureTime.getTime() + durationHours * 60L * 60 * 1000);
            } else if (isDaily) {
                effectiveEndTime = new Timestamp(departureTime.getTime() + durationDays * 24L * 60 * 60 * 1000);
            } else if (needsDistance) {
                // ONE_WAY tuyến tự do/cố định không có returnTime -> ước tính thời gian
                // di chuyển theo khoảng cách thật (tốc độ trung bình 40km/h), tối thiểu 30 phút.
                long estimatedMinutes = Math.max(30, Math.round((distanceKm / 40.0) * 60));
                effectiveEndTime = new Timestamp(departureTime.getTime() + estimatedMinutes * 60 * 1000);
            }
        }

        // ---- BR-27: Validate không trùng lịch (check sớm, xem lưu ý ở BR-22 phía trên —
        // check thật/authoritative nằm trong BookingDAO.createBooking có khóa dòng Vehicle) ----
        boolean hasConflict = bookingDAO.isVehicleScheduleConflict(vehicleId, departureTime, effectiveEndTime);
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
        // Lưu giờ kết thúc đã tính (effectiveEndTime) thay vì returnTime gốc có thể null,
        // để các lần check trùng lịch sau này (kể cả của booking khác) luôn có mốc chính xác.
        detail.setReturnTime(effectiveEndTime);

        if (needsDistance) {
            // Quãng đường / tuyến cố định: lưu tọa độ + km thật từ Maps API
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

        // ---- Snapshot đơn giá lúc đặt (cho tính năng gia hạn/quá giờ) ----
        // Lấy CẢ 2 rate (HOURLY và DAILY) của cùng vehicleType này, không chỉ rate của
        // đúng bookingType hiện tại — vì booking DAILY cũng cần biết PricePerHour để
        // tính tiền quá giờ (luôn tính theo giờ, kể cả với DAILY). getPricingRule trả
        // về null (không throw) nếu vehicleType này không có rule cho loại đó.
        model.PricingRule hourlyRuleSnap = customerBookingDAO.getPricingRule(vehicleId, "HOURLY", tripDirection);
        model.PricingRule dailyRuleSnap = customerBookingDAO.getPricingRule(vehicleId, "DAILY", tripDirection);
        pricing.setPricePerHourSnapshot(hourlyRuleSnap != null ? hourlyRuleSnap.getPricePerHour() : null);
        pricing.setPricePerDaySnapshot(dailyRuleSnap != null ? dailyRuleSnap.getPricePerDay() : null);

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