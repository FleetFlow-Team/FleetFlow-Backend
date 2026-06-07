package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class BookingPricing extends BaseEntity {
    private Long bookingId;
    private Long driverId;
    private Long ruleId;
    private BigDecimal baseFare;
    private BigDecimal weekendSurcharge;
    private BigDecimal discountAmount;
    private BigDecimal estimatedTotal;
    private Long approvedBy;
    private Timestamp approvedAt;
 
    public BookingPricing() {}
 
    public BookingPricing(Long bookingId, Long driverId, Long ruleId,
                          BigDecimal baseFare, BigDecimal weekendSurcharge,
                          BigDecimal discountAmount, BigDecimal estimatedTotal,
                          Long approvedBy, Timestamp approvedAt) {
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
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
 
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
 
    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }
 
    public BigDecimal getWeekendSurcharge() { return weekendSurcharge; }
    public void setWeekendSurcharge(BigDecimal weekendSurcharge) { this.weekendSurcharge = weekendSurcharge; }
 
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
 
    public BigDecimal getEstimatedTotal() { return estimatedTotal; }
    public void setEstimatedTotal(BigDecimal estimatedTotal) { this.estimatedTotal = estimatedTotal; }
 
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
 
    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }
}