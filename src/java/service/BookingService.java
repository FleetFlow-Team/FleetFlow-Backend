package service;

import dao.BookingDAO;
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.Booking;
import model.BookingDetail;

public class BookingService {

    private final BookingDAO bookingDAO = new BookingDAO();
    private final MapsService mapsService = new MapsService();

    /**
     * Tạo booking mới — full flow:
     * 1. Validate khoảng cách >= 20km (BR-01)
     * 2. Validate thời gian đặt trước >= 120 phút (BR-02)
     * 3. Validate xe AVAILABLE (BR-22)
     * 4. Validate không trùng lịch xe (BR-27)
     * 5. Insert Booking + BookingDetail vào DB
     */
    public long createBooking(
            int customerId,
            int vehicleId,
            Integer voucherId,
            String bookingType,
            String tripDirection,
            String pickupAddress,
            double pickupLat,
            double pickupLng,
            String dropoffAddress,
            double dropoffLat,
            double dropoffLng,
            Timestamp departureTime,
            Timestamp returnTime
    ) throws Exception {

        // ---- BR-01: Validate khoảng cách tối thiểu 20km ----
        double distanceKm = mapsService.validateAndGetDistance(
                pickupLat, pickupLng,
                dropoffLat, dropoffLng
        );

        // ---- BR-02: Validate thời gian đặt trước tối thiểu 120 phút ----
        long now = System.currentTimeMillis();
        long diffMinutes = (departureTime.getTime() - now) / (1000 * 60);
        if (diffMinutes < 120) {
            throw new IllegalArgumentException(
                "Phải đặt xe trước giờ khởi hành tối thiểu 120 phút. "
                + "Hiện tại chỉ còn " + diffMinutes + " phút."
            );
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
        if (voucherId != null) { booking.setVoucherId(voucherId); }
        booking.setBookingType(bookingType);
        booking.setTripDirection(tripDirection);
        booking.setStatus("PENDING");
        booking.setCreatedAt(new Timestamp(now));

        // ---- Tạo BookingDetail object ----
        BookingDetail detail = new BookingDetail();
        detail.setPickupAddress(pickupAddress);
        detail.setPickupLat(BigDecimal.valueOf(pickupLat));
        detail.setPickupLng(BigDecimal.valueOf(pickupLng));
        detail.setDropoffAddress(dropoffAddress);
        detail.setDropoffLat(BigDecimal.valueOf(dropoffLat));
        detail.setDropoffLng(BigDecimal.valueOf(dropoffLng));
        detail.setDistanceKm(BigDecimal.valueOf(distanceKm));  // lưu khoảng cách
        detail.setDepartureTime(departureTime);
        detail.setReturnTime(returnTime);

        // ---- Insert vào DB ----
        return bookingDAO.createBooking(booking, detail);
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
     * Cập nhật trạng thái booking
     */
    public void updateBookingStatus(int bookingId, String status) throws Exception {
        bookingDAO.updateStatus(bookingId, status);
    }
}