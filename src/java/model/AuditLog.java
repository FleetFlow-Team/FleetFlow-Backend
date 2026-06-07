package model;
 
import java.sql.Timestamp;
 
public class AuditLog {
    private Long id;
    private Long accountId;
    private String action;
    private String entityName;
    private String entityId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private Timestamp createdAt;
 
    public AuditLog() {}
 
    public AuditLog(Long accountId, String action, String entityName, String entityId,
                    String oldValue, String newValue, String ipAddress, Timestamp createdAt) {
        this.accountId = accountId;
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
 
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
 
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
 
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
 
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
 
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
 
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
 
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}