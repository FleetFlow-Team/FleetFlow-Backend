package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class Invoice extends BaseEntity {
    private Long bookingId;
    private BigDecimal baseFare;
    private BigDecimal weekendSurcharge;
    private BigDecimal tollSurchargeTotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String status;
    private Timestamp issuedAt;
 
    public Invoice() {}
 
    public Invoice(Long bookingId, BigDecimal baseFare, BigDecimal weekendSurcharge,
                   BigDecimal tollSurchargeTotal, BigDecimal discountAmount,
                   BigDecimal totalAmount, String status, Timestamp issuedAt) {
        this.bookingId = bookingId;
        this.baseFare = baseFare;
        this.weekendSurcharge = weekendSurcharge;
        this.tollSurchargeTotal = tollSurchargeTotal;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.status = status;
        this.issuedAt = issuedAt;
    }
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }
 
    public BigDecimal getWeekendSurcharge() { return weekendSurcharge; }
    public void setWeekendSurcharge(BigDecimal weekendSurcharge) { this.weekendSurcharge = weekendSurcharge; }
 
    public BigDecimal getTollSurchargeTotal() { return tollSurchargeTotal; }
    public void setTollSurchargeTotal(BigDecimal tollSurchargeTotal) { this.tollSurchargeTotal = tollSurchargeTotal; }
 
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
 
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Timestamp getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Timestamp issuedAt) { this.issuedAt = issuedAt; }
}