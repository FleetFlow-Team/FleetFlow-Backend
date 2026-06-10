package model;

import java.sql.Timestamp;
import model.base.BaseEntity;

public class IdentityDocument extends BaseEntity {
    private int ownerAccountId;
    private String ownerType;
    private String docType;
    private String nationalId;
    private String secureFileUrl;
    private String status;
    private int verifiedBy;
    private Timestamp uploadedAt;
    private Timestamp verifiedAt;

    //constructor
    public IdentityDocument()
    {
        
    }
    public IdentityDocument(int ownerAccountId, String ownerType, String docType,
                            String nationalId, String secureFileUrl, String status,
                            int verifiedBy, Timestamp uploadedAt, Timestamp verifiedAt) {
        this.ownerAccountId = ownerAccountId;
        this.ownerType = ownerType;
        this.docType = docType;
        this.nationalId = nationalId;
        this.secureFileUrl = secureFileUrl;
        this.status = status;
        this.verifiedBy = verifiedBy;
        this.uploadedAt = uploadedAt;
        this.verifiedAt = verifiedAt;
    }

    public int getOwnerAccountId() { return ownerAccountId; }
    public void setOwnerAccountId(int ownerAccountId) { this.ownerAccountId = ownerAccountId; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getSecureFileUrl() { return secureFileUrl; }
    public void setSecureFileUrl(String secureFileUrl) { this.secureFileUrl = secureFileUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(int verifiedBy) { this.verifiedBy = verifiedBy; }

    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }

    public Timestamp getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Timestamp verifiedAt) { this.verifiedAt = verifiedAt; }
}