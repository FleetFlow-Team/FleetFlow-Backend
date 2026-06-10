/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

/**
 *
 * @author User
 */
public class Customer extends BaseEntity implements IAuditableEntity {
    private String address;
    private BigDecimal debtBalance;
    private int accountId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    //constructor
    
    public Customer() {
    }

    public Customer(String address, BigDecimal debtBalance, int accountId, Timestamp createdAt, Timestamp updatedAt) {
        this.address = address;
        this.debtBalance = debtBalance;
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getDebtBalance() {
        return debtBalance;
    }

    public void setDebtBalance(BigDecimal debtBalance) {
        this.debtBalance = debtBalance;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
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
