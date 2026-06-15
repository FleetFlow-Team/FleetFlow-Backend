package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

public class Driver extends BaseEntity implements IAuditableEntity {
    private String approvalStatus;
    private int accountId;
    private String availabilityStatus;
    private Timestamp termsAcceptedAt;
    private BigDecimal averageRating;
    private BigDecimal walletBalance;
    private boolean termsAccepted; // 🚀 PARAM MỚI THÊM VÀO
    
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    public Driver() {
    }

    public Driver(int accountId, String approvalStatus, String availabilityStatus, Timestamp termsAcceptedAt, BigDecimal averageRating, BigDecimal walletBalance, boolean termsAccepted, Timestamp createdAt, Timestamp updatedAt) {
        this.approvalStatus = approvalStatus;
        this.availabilityStatus = availabilityStatus;
        this.termsAcceptedAt = termsAcceptedAt;
        this.averageRating = averageRating;
        this.walletBalance = walletBalance;
        this.accountId = accountId;
        this.termsAccepted = termsAccepted; // 🚀
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- Các hàm Getter và Setter cũ giữ nguyên ---

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(String availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public Timestamp getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(Timestamp termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }

    // 🚀 GETTER/SETTER CHO PARAM MỚI THÊM
    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }

    @Override
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Timestamp getUpdatedAt() {
         return updatedAt;
    }
    
    @Override
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}