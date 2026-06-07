package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class Surcharge extends BaseEntity {
    private Long bookingId;
    private String surchargeType;
    private BigDecimal amount;
    private String receiptImageUrl;
    private String approvalStatus;
    private Long approvedBy;
    private Timestamp declaredAt;
 
    public Surcharge() {}
 
    public Surcharge(Long bookingId, String surchargeType, BigDecimal amount,
                     String receiptImageUrl, Timestamp declaredAt) {
        this.bookingId = bookingId;
        this.surchargeType = surchargeType;
        this.amount = amount;
        this.receiptImageUrl = receiptImageUrl;
        this.declaredAt = declaredAt;
    }
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public String getSurchargeType() { return surchargeType; }
    public void setSurchargeType(String surchargeType) { this.surchargeType = surchargeType; }
 
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
 
    public String getReceiptImageUrl() { return receiptImageUrl; }
    public void setReceiptImageUrl(String receiptImageUrl) { this.receiptImageUrl = receiptImageUrl; }
 
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
 
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
 
    public Timestamp getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(Timestamp declaredAt) { this.declaredAt = declaredAt; }
}