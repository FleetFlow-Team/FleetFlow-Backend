package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class Voucher extends BaseEntity {
    private Long campaignId;
    private String code;
    private String discountType;        // PERCENT, FIXED
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minBookingValue;
    private Long applicableVehicleTypeId;
    private Integer maxUsagePerUser;
    private Timestamp validFrom;
    private Timestamp validTo;
    private String status;
    private Long createdBy;
 
    public Voucher() {}
 
    public Voucher(Long campaignId, String code, String discountType,
                   BigDecimal discountValue, BigDecimal maxDiscountAmount,
                   BigDecimal minBookingValue, Long applicableVehicleTypeId,
                   Integer maxUsagePerUser, Timestamp validFrom,
                   Timestamp validTo, String status, Long createdBy) {
        this.campaignId = campaignId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minBookingValue = minBookingValue;
        this.applicableVehicleTypeId = applicableVehicleTypeId;
        this.maxUsagePerUser = maxUsagePerUser;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = status;
        this.createdBy = createdBy;
    }
 
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
 
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
 
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
 
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
 
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
 
    public BigDecimal getMinBookingValue() { return minBookingValue; }
    public void setMinBookingValue(BigDecimal minBookingValue) { this.minBookingValue = minBookingValue; }
 
    public Long getApplicableVehicleTypeId() { return applicableVehicleTypeId; }
    public void setApplicableVehicleTypeId(Long id) { this.applicableVehicleTypeId = id; }
 
    public Integer getMaxUsagePerUser() { return maxUsagePerUser; }
    public void setMaxUsagePerUser(Integer maxUsagePerUser) { this.maxUsagePerUser = maxUsagePerUser; }
 
    public Timestamp getValidFrom() { return validFrom; }
    public void setValidFrom(Timestamp validFrom) { this.validFrom = validFrom; }
 
    public Timestamp getValidTo() { return validTo; }
    public void setValidTo(Timestamp validTo) { this.validTo = validTo; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}