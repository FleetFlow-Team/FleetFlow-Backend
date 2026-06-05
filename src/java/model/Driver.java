/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

/**
 *
 * @author User
 */
public class Driver extends BaseEntity implements IAuditableEntity{
    private String approvalStatus;
    private Long accountId;
    private String availabilityStatus;
    private Timestamp termsAcceptedAt;
    private BigDecimal averageRating;
    private BigDecimal walletBalance;
    
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    //constructor
    
    public Driver(Long accountId, String approvalStatus, String availabilityStatus, Timestamp termsAcceptedAt, BigDecimal averageRating, BigDecimal walletBalance, Timestamp createdAt, Timestamp updatedAt) {
        this.approvalStatus = approvalStatus;
        this.availabilityStatus = availabilityStatus;
        this.termsAcceptedAt = termsAcceptedAt;
        this.averageRating = averageRating;
        this.walletBalance = walletBalance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
