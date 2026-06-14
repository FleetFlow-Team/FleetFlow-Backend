package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Hồ sơ khách hàng = Account (thông tin tài khoản) + Customer (thông tin khách).
 * Dùng cho BE-3 (xem) và trả lại sau BE-4 (cập nhật).
 */
public class Customer {

    private int accountId;
    private int customerId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String roleName;
    private String status;        // Account.Status: Active / Locked
    private String address;
    private BigDecimal debtBalance;
    private String bookingStatus; // Customer.BookingStatus: Active / Suspended
    private Timestamp createdAt;

    public Customer() {
    }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getDebtBalance() { return debtBalance; }
    public void setDebtBalance(BigDecimal debtBalance) { this.debtBalance = debtBalance; }

    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
