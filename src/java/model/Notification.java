package model;
 
import java.sql.Timestamp;
 
public class Notification {
    private Long id;
    private Long recipientAccountId;
    private Long bookingId;
    private String title;
    private String message;
    private String type;
    private String channel;   // IN_APP, EMAIL, BOTH
    private Boolean isRead;
    private Timestamp createdAt;
 
    public Notification() {}
 
    public Notification(Long recipientAccountId, Long bookingId, String title,
                        String message, String type, String channel, Timestamp createdAt) {
        this.recipientAccountId = recipientAccountId;
        this.bookingId = bookingId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.channel = channel;
        this.isRead = false;
        this.createdAt = createdAt;
    }
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public Long getRecipientAccountId() { return recipientAccountId; }
    public void setRecipientAccountId(Long recipientAccountId) { this.recipientAccountId = recipientAccountId; }
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
 
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
 
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
 
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
 
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
 
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}