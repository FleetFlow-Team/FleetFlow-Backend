package model;

import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

public class Booking extends BaseEntity implements IAuditableEntity {

    private int customerId;
    private int vehicleId;
    private int voucherId;
    private String bookingType;    // HOURLY, DAILY, DISTANCE, INNER_CITY, INTER_CITY
    private String tripDirection;  // ONE_WAY, ROUND_TRIP
    private String status;
    private String note;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String customerName;
    private String customerPhone;
    private String completionPhotoUrl; // Ảnh driver chụp xác nhận đã đến điểm trả khách (kèm bấm nút "complete")

    public Booking() {
    }

    public Booking(int customerId, int vehicleId, int voucherId,
            String bookingType, String tripDirection,
            String status, Timestamp createdAt) {
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.voucherId = voucherId;
        this.bookingType = bookingType;
        this.tripDirection = tripDirection;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    
    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public int getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(int voucherId) {
        this.voucherId = voucherId;
    }

    public String getBookingType() {
        return bookingType;
    }

    public void setBookingType(String bookingType) {
        this.bookingType = bookingType;
    }

    public String getTripDirection() {
        return tripDirection;
    }

    public void setTripDirection(String tripDirection) {
        this.tripDirection = tripDirection;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCompletionPhotoUrl() {
        return completionPhotoUrl;
    }

    public void setCompletionPhotoUrl(String completionPhotoUrl) {
        this.completionPhotoUrl = completionPhotoUrl;
    }
}