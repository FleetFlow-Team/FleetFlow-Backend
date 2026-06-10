package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class Cancellation extends BaseEntity {
    private int bookingId;
    private Integer penaltyPercent;
    private BigDecimal penaltyAmount;
    private String penaltyStatus;
    private String reason;
    private Timestamp cancelledAt;
 
    public Cancellation() {}
 
    public Cancellation(int bookingId, Integer penaltyPercent, BigDecimal penaltyAmount,
                        String penaltyStatus, String reason, Timestamp cancelledAt) {
        this.bookingId = bookingId;
        this.penaltyPercent = penaltyPercent;
        this.penaltyAmount = penaltyAmount;
        this.penaltyStatus = penaltyStatus;
        this.reason = reason;
        this.cancelledAt = cancelledAt;
    }
 
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
 
    public Integer getPenaltyPercent() { return penaltyPercent; }
    public void setPenaltyPercent(Integer penaltyPercent) { this.penaltyPercent = penaltyPercent; }
 
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }
 
    public String getPenaltyStatus() { return penaltyStatus; }
    public void setPenaltyStatus(String penaltyStatus) { this.penaltyStatus = penaltyStatus; }
 
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
 
    public Timestamp getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Timestamp cancelledAt) { this.cancelledAt = cancelledAt; }
}