package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
 
public class CustomerWalletLedger {
    private Long id;
    private Long customerId;
    private BigDecimal amount;
    private String transactionType;   // PENALTY, PAYMENT, REFUND
    private Long bookingId;
    private Timestamp createdAt;
 
    public CustomerWalletLedger() {}
 
    public CustomerWalletLedger(Long customerId, BigDecimal amount,
                                String transactionType, Long bookingId, Timestamp createdAt) {
        this.customerId = customerId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.bookingId = bookingId;
        this.createdAt = createdAt;
    }
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
 
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
 
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
 
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
 
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}