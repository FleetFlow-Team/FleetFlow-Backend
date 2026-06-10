package model;
 
import java.sql.Timestamp;
 
public class TripEventLog {
    private Long id;
    private int bookingId;
    private String eventType;
    private String notes;
    private Timestamp eventTime;
 
    public TripEventLog() {}
 
    public TripEventLog(int bookingId, String eventType,
                        String notes, Timestamp eventTime) {
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.notes = notes;
        this.eventTime = eventTime;
    }
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
 
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
 
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
 
    public Timestamp getEventTime() { return eventTime; }
    public void setEventTime(Timestamp eventTime) { this.eventTime = eventTime; }
}