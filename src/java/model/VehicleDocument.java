package model;
 
import java.sql.Date;
import model.base.BaseEntity;
 
public class VehicleDocument extends BaseEntity {
    private int vehicleId;
    private String docType;
    private String secureFileUrl;
    private Date issueDate;
    private Date expiryDate;
 
    public VehicleDocument() {}
 
    public VehicleDocument(int vehicleId, String docType, String secureFileUrl,
                           Date issueDate, Date expiryDate) {
        this.vehicleId = vehicleId;
        this.docType = docType;
        this.secureFileUrl = secureFileUrl;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
    }
 
    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }
 
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
 
    public String getSecureFileUrl() { return secureFileUrl; }
    public void setSecureFileUrl(String secureFileUrl) { this.secureFileUrl = secureFileUrl; }
 
    public Date getIssueDate() { return issueDate; }
    public void setIssueDate(Date issueDate) { this.issueDate = issueDate; }
 
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
}