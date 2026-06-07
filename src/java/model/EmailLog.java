package model;
 
import java.sql.Timestamp;
 
public class EmailLog {
    private Long id;
    private Long campaignId;
    private Long recipientAccountId;
    private String subject;
    private String status;
    private Timestamp sentAt;
 
    public EmailLog() {}
 
    public EmailLog(Long campaignId, Long recipientAccountId,
                    String subject, String status, Timestamp sentAt) {
        this.campaignId = campaignId;
        this.recipientAccountId = recipientAccountId;
        this.subject = subject;
        this.status = status;
        this.sentAt = sentAt;
    }
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
 
    public Long getRecipientAccountId() { return recipientAccountId; }
    public void setRecipientAccountId(Long recipientAccountId) { this.recipientAccountId = recipientAccountId; }
 
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }
}