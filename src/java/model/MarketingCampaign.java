package model;
 
import java.sql.Timestamp;
 
public class MarketingCampaign {
    private int id;
    private String name;
    private String triggerCondition;
    private String emailTemplate;
    private String status;
    private Timestamp lastRunAt;
    private int createdBy;
    private Timestamp createdAt;
 
    public MarketingCampaign() {}
 
    public MarketingCampaign(String name, String triggerCondition, String emailTemplate,
                             String status, int createdBy, Timestamp createdAt) {
        this.name = name;
        this.triggerCondition = triggerCondition;
        this.emailTemplate = emailTemplate;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
 
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
 
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
 
    public String getTriggerCondition() { return triggerCondition; }
    public void setTriggerCondition(String triggerCondition) { this.triggerCondition = triggerCondition; }
 
    public String getEmailTemplate() { return emailTemplate; }
    public void setEmailTemplate(String emailTemplate) { this.emailTemplate = emailTemplate; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Timestamp getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Timestamp lastRunAt) { this.lastRunAt = lastRunAt; }
 
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
 
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}