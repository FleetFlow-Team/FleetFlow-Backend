package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
 
public class CustomerWalletLedger {
    private int id;
    private int customerId;
    private BigDecimal amount;
    private String transactionType;   // PENALTY, PAYMENT, REFUND
    private int bookingId;
    private Timestamp createdAt;
 
    public CustomerWalletLedger() {}
 
    public CustomerWalletLedger(int customerId, BigDecimal amount,
                                String transactionType, int bookingId, Timestamp createdAt) {
        this.customerId = customerId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.bookingId = bookingId;
        this.createdAt = createdAt;
    }
 
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
 
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
 
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
 
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
 
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
 
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}