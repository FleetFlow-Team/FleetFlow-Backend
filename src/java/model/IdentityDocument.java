/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

/**
 *
 * @author User
 */
public class IdentityDocument extends BaseEntity implements IAuditableEntity {
    private Long ownerAccountId;
    private String ownerType;
    private String docType;
    private String nationalId;
    private String secureFileUrl;
    private String status;
    private Long verifiedBy;
    private Timestamp uploaddedAt;
    private Timestamp verifieddAt;
    
    private Timestamp createdAt;
    private Timestamp updatedAt;

    //constructor
    
    public IdentityDocument(Long ownerAccountId, String ownerType, String docType, String nationalId, String secureFileUrl, String status, Long verifiedBy, Timestamp uploaddedAt, Timestamp verifieddAt, Timestamp createdAt, Timestamp updatedAt) {
        this.ownerAccountId = ownerAccountId;
        this.ownerType = ownerType;
        this.docType = docType;
        this.nationalId = nationalId;
        this.secureFileUrl = secureFileUrl;
        this.status = status;
        this.verifiedBy = verifiedBy;
        this.uploaddedAt = uploaddedAt;
        this.verifieddAt = verifieddAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getSecureFileUrl() {
        return secureFileUrl;
    }

    public void setSecureFileUrl(String secureFileUrl) {
        this.secureFileUrl = secureFileUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Long verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Timestamp getUploaddedAt() {
        return uploaddedAt;
    }

    public void setUploaddedAt(Timestamp uploaddedAt) {
        this.uploaddedAt = uploaddedAt;
    }

    public Timestamp getVerifieddAt() {
        return verifieddAt;
    }

    public void setVerifieddAt(Timestamp verifieddAt) {
        this.verifieddAt = verifieddAt;
    }

    
    @Override
    public Timestamp getCreatedAt() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void setCreatedAt(Timestamp createdAt) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public Timestamp getUpdatedAt() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void setUpdatedAt(Timestamp updatedAt) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
