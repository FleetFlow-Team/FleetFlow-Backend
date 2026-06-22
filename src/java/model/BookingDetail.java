package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;

public class BookingDetail extends BaseEntity {

    private int bookingId;
    private String pickupAddress;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private BigDecimal distanceKm;
    private String dropoffAddress;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private Timestamp departureTime;
    private Timestamp returnTime;
    private Integer durationHours;
    private Integer durationDays;

    // --- Chiều về (chỉ có giá trị khi tripDirection = ROUND_TRIP) ---
    private String returnPickupAddress;
    private BigDecimal returnPickupLat;
    private BigDecimal returnPickupLng;
    private String returnDropoffAddress;
    private BigDecimal returnDropoffLat;
    private BigDecimal returnDropoffLng;
    private BigDecimal returnDistanceKm;

    public BookingDetail() {
    }

    public BookingDetail(int bookingId, String pickupAddress, BigDecimal pickupLat,
            BigDecimal pickupLng, String dropoffAddress, BigDecimal dropoffLat,
            BigDecimal dropoffLng, Timestamp departureTime, Timestamp returnTime, BigDecimal distanceKm) {
        this.bookingId = bookingId;
        this.pickupAddress = pickupAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffAddress = dropoffAddress;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.departureTime = departureTime;
        this.returnTime = returnTime;
        this.distanceKm = distanceKm;
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public BigDecimal getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(BigDecimal pickupLat) {
        this.pickupLat = pickupLat;
    }

    public BigDecimal getPickupLng() {
        return pickupLng;
    }

    public void setPickupLng(BigDecimal pickupLng) {
        this.pickupLng = pickupLng;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public void setDropoffAddress(String dropoffAddress) {
        this.dropoffAddress = dropoffAddress;
    }

    public BigDecimal getDropoffLat() {
        return dropoffLat;
    }

    public void setDropoffLat(BigDecimal dropoffLat) {
        this.dropoffLat = dropoffLat;
    }

    public BigDecimal getDropoffLng() {
        return dropoffLng;
    }

    public void setDropoffLng(BigDecimal dropoffLng) {
        this.dropoffLng = dropoffLng;
    }

    public Timestamp getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Timestamp departureTime) {
        this.departureTime = departureTime;
    }

    public Timestamp getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(Timestamp returnTime) {
        this.returnTime = returnTime;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(Integer durationHours) {
        this.durationHours = durationHours;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    // --- Getters / Setters chiều về (ROUND_TRIP) ---
    public String getReturnPickupAddress() {
        return returnPickupAddress;
    }

    public void setReturnPickupAddress(String v) {
        this.returnPickupAddress = v;
    }

    public BigDecimal getReturnPickupLat() {
        return returnPickupLat;
    }

    public void setReturnPickupLat(BigDecimal v) {
        this.returnPickupLat = v;
    }

    public BigDecimal getReturnPickupLng() {
        return returnPickupLng;
    }

    public void setReturnPickupLng(BigDecimal v) {
        this.returnPickupLng = v;
    }

    public String getReturnDropoffAddress() {
        return returnDropoffAddress;
    }

    public void setReturnDropoffAddress(String v) {
        this.returnDropoffAddress = v;
    }

    public BigDecimal getReturnDropoffLat() {
        return returnDropoffLat;
    }

    public void setReturnDropoffLat(BigDecimal v) {
        this.returnDropoffLat = v;
    }

    public BigDecimal getReturnDropoffLng() {
        return returnDropoffLng;
    }

    public void setReturnDropoffLng(BigDecimal v) {
        this.returnDropoffLng = v;
    }

    public BigDecimal getReturnDistanceKm() {
        return returnDistanceKm;
    }

    public void setReturnDistanceKm(BigDecimal v) {
        this.returnDistanceKm = v;
    }
}
