package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class BookingPricing extends BaseEntity {
    private int bookingId;
    private int driverId;
    private int ruleId;
    private BigDecimal baseFare;
    private BigDecimal weekendSurcharge;
    private BigDecimal discountAmount;
    private BigDecimal estimatedTotal;
    private int approvedBy;
    private Timestamp approvedAt;
 
    public BookingPricing() {}
 
    public BookingPricing(int bookingId, int driverId, int ruleId,
                          BigDecimal baseFare, BigDecimal weekendSurcharge,
                          BigDecimal discountAmount, BigDecimal estimatedTotal,
                          int approvedBy, Timestamp approvedAt) {
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.ruleId = ruleId;
        this.baseFare = baseFare;
        this.weekendSurcharge = weekendSurcharge;
        this.discountAmount = discountAmount;
        this.estimatedTotal = estimatedTotal;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }
 
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
 
    public int getDriverId() { return driverId; }
    public void setDriverId(int driverId) { this.driverId = driverId; }
 
    public int getRuleId() { return ruleId; }
    public void setRuleId(int ruleId) { this.ruleId = ruleId; }
 
    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }
 
    public BigDecimal getWeekendSurcharge() { return weekendSurcharge; }
    public void setWeekendSurcharge(BigDecimal weekendSurcharge) { this.weekendSurcharge = weekendSurcharge; }
 
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
 
    public BigDecimal getEstimatedTotal() { return estimatedTotal; }
    public void setEstimatedTotal(BigDecimal estimatedTotal) { this.estimatedTotal = estimatedTotal; }
 
    public int getApprovedBy() { return approvedBy; }
    public void setApprovedBy(int approvedBy) { this.approvedBy = approvedBy; }
 
    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }
}