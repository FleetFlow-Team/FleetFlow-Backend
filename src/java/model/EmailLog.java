package model;
 
import java.sql.Timestamp;
 
public class EmailLog {
    private int id;
    private int campaignId;
    private int recipientAccountId;
    private String subject;
    private String status;
    private Timestamp sentAt;
 
    public EmailLog() {}
 
    public EmailLog(int campaignId, int recipientAccountId,
                    String subject, String status, Timestamp sentAt) {
        this.campaignId = campaignId;
        this.recipientAccountId = recipientAccountId;
        this.subject = subject;
        this.status = status;
        this.sentAt = sentAt;
    }
 
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
 
    public int getCampaignId() { return campaignId; }
    public void setCampaignId(int campaignId) { this.campaignId = campaignId; }
 
    public int getRecipientAccountId() { return recipientAccountId; }
    public void setRecipientAccountId(int recipientAccountId) { this.recipientAccountId = recipientAccountId; }
 
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }
}