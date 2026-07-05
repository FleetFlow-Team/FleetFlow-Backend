package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class Payment extends BaseEntity {
    private int invoiceId;
    private String paymentType;   // DEPOSIT, FINAL
    private String method;        // VNPAY, CASH, BANK_TRANSFER
    private BigDecimal amount;
    private String status;
    private String transactionRef;
    private Timestamp paidAt;
 
    public Payment() {}
 
    public Payment(int invoiceId, String paymentType, String method,
                   BigDecimal amount, String status,
                   String transactionRef, Timestamp paidAt) {
        this.invoiceId = invoiceId;
        this.paymentType = paymentType;
        this.method = method;
        this.amount = amount;
        this.status = status;
        this.transactionRef = transactionRef;
        this.paidAt = paidAt;
    }
 
    public int getInvoiceId() { return invoiceId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }
 
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
 
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
 
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
 
    public Timestamp getPaidAt() { return paidAt; }
    public void setPaidAt(Timestamp paidAt) { this.paidAt = paidAt; }
}