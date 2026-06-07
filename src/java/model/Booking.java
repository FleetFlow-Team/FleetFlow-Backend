package model;
 
import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;
 
public class Booking extends BaseEntity implements IAuditableEntity {
    private Long customerId;
    private Long vehicleId;
    private Long voucherId;
    private String bookingType;    // HOURLY, DAILY, DISTANCE, INNER_CITY, INTER_CITY
    private String tripDirection;  // ONE_WAY, ROUND_TRIP
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
 
    public Booking() {}
 
    public Booking(Long customerId, Long vehicleId, Long voucherId,
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
 
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
 
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
 
    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
 
    public String getBookingType() { return bookingType; }
    public void setBookingType(String bookingType) { this.bookingType = bookingType; }
 
    public String getTripDirection() { return tripDirection; }
    public void setTripDirection(String tripDirection) { this.tripDirection = tripDirection; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    @Override public Timestamp getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    @Override public Timestamp getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}