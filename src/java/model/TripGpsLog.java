package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
 
public class TripGpsLog {
    private Long id;
    private Long bookingId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Timestamp recordedAt;
 
    public TripGpsLog() {}
 
    public TripGpsLog(Long bookingId, BigDecimal latitude,
                      BigDecimal longitude, Timestamp recordedAt) {
        this.bookingId = bookingId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
 
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
 
    public Timestamp getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Timestamp recordedAt) { this.recordedAt = recordedAt; }
}