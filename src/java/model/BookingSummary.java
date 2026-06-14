package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Một dòng trong lịch sử đặt xe của khách (BE-7).
 * Gộp Booking + Vehicle + BookingDetail + BookingPricing.
 */
public class BookingSummary {

    private int bookingId;
    private String status;
    private String bookingType;
    private String tripDirection;
    private Timestamp createdAt;

    // Thông tin xe
    private String brand;
    private String model;
    private String licensePlate;

    // Lộ trình
    private String pickupAddress;
    private String dropoffAddress;
    private BigDecimal distanceKm;
    private Timestamp departureTime;

    // Tài chính
    private BigDecimal estimatedTotal;

    public BookingSummary() {
    }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBookingType() { return bookingType; }
    public void setBookingType(String bookingType) { this.bookingType = bookingType; }

    public String getTripDirection() { return tripDirection; }
    public void setTripDirection(String tripDirection) { this.tripDirection = tripDirection; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public Timestamp getDepartureTime() { return departureTime; }
    public void setDepartureTime(Timestamp departureTime) { this.departureTime = departureTime; }

    public BigDecimal getEstimatedTotal() { return estimatedTotal; }
    public void setEstimatedTotal(BigDecimal estimatedTotal) { this.estimatedTotal = estimatedTotal; }
}
