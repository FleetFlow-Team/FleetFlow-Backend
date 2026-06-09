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
     * 1. Geocode địa chỉ → tọa độ (nếu chưa có tọa độ)
     * 2. Validate khoảng cách >= 20km (BR-01)
     * 3. Validate thời gian đặt trước >= 120 phút (BR-02)
     * 4. Insert Booking + BookingDetail vào DB
     *
     * @return BookingID vừa tạo
     */
    public long createBooking(
            long customerId,
            long vehicleId,
            Long voucherId,
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

        // ---- Tạo Booking object ----
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setVehicleId(vehicleId);
        booking.setVoucherId(voucherId);
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
        detail.setDepartureTime(departureTime);
        detail.setReturnTime(returnTime);

        // ---- Insert vào DB ----
        long bookingId = bookingDAO.createBooking(booking, detail);
        return bookingId;
    }

    /**
     * Geocode địa chỉ text → tọa độ
     * Dùng khi frontend chỉ có địa chỉ text, chưa có tọa độ
     */
    public double[] geocodeAddress(String address) throws Exception {
        return mapsService.geocode(address);
    }

    /**
     * Lấy thông tin booking theo ID
     */
    public Booking getBookingById(long bookingId) throws Exception {
        return bookingDAO.findById(bookingId);
    }

    /**
     * Lấy BookingDetail theo BookingID
     */
    public BookingDetail getBookingDetail(long bookingId) throws Exception {
        return bookingDAO.findDetailByBookingId(bookingId);
    }

    /**
     * Cập nhật trạng thái booking
     * Các trạng thái: PENDING → APPROVED → IN_PROGRESS → COMPLETED / CANCELLED
     */
    public void updateBookingStatus(long bookingId, String status) throws Exception {
        bookingDAO.updateStatus(bookingId, status);
    }
}