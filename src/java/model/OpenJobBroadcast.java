package model;
 
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class OpenJobBroadcast extends BaseEntity {
    private Long bookingId;
    private Long claimedByDriverId;
    private String status;
    private Timestamp broadcastAt;
    private Timestamp expiresAt;
    private Timestamp claimedAt;
 
    public OpenJobBroadcast() {}
 
    public OpenJobBroadcast(Long bookingId, String status,
                            Timestamp broadcastAt, Timestamp expiresAt) {
        this.bookingId = bookingId;
        this.status = status;
        this.broadcastAt = broadcastAt;
        this.expiresAt = expiresAt;
    }
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public Long getClaimedByDriverId() { return claimedByDriverId; }
    public void setClaimedByDriverId(Long claimedByDriverId) { this.claimedByDriverId = claimedByDriverId; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Timestamp getBroadcastAt() { return broadcastAt; }
    public void setBroadcastAt(Timestamp broadcastAt) { this.broadcastAt = broadcastAt; }
 
    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }
 
    public Timestamp getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Timestamp claimedAt) { this.claimedAt = claimedAt; }
}