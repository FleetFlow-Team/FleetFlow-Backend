package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
 
public class DriverEarning {
    private int id;
    private int driverId;
    private int bookingId;
    private String earningType;   // TRIP, CANCELLATION_BONUS
    private BigDecimal fareShare;
    private BigDecimal surchargeShare;
    private BigDecimal cancellationCompensation;
    private BigDecimal companyCommission;
    private BigDecimal netAmount;
    private Timestamp createdAt;
 
    public DriverEarning() {}
 
    public DriverEarning(int driverId, int bookingId, String earningType,
                         BigDecimal fareShare, BigDecimal surchargeShare,
                         BigDecimal cancellationCompensation, BigDecimal companyCommission,
                         BigDecimal netAmount, Timestamp createdAt) {
        this.driverId = driverId;
        this.bookingId = bookingId;
        this.earningType = earningType;
        this.fareShare = fareShare;
        this.surchargeShare = surchargeShare;
        this.cancellationCompensation = cancellationCompensation;
        this.companyCommission = companyCommission;
        this.netAmount = netAmount;
        this.createdAt = createdAt;
    }
 
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
 
    public int getDriverId() { return driverId; }
    public void setDriverId(int driverId) { this.driverId = driverId; }
 
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
 
    public String getEarningType() { return earningType; }
    public void setEarningType(String earningType) { this.earningType = earningType; }
 
    public BigDecimal getFareShare() { return fareShare; }
    public void setFareShare(BigDecimal fareShare) { this.fareShare = fareShare; }
 
    public BigDecimal getSurchargeShare() { return surchargeShare; }
    public void setSurchargeShare(BigDecimal surchargeShare) { this.surchargeShare = surchargeShare; }
 
    public BigDecimal getCancellationCompensation() { return cancellationCompensation; }
    public void setCancellationCompensation(BigDecimal v) { this.cancellationCompensation = v; }
 
    public BigDecimal getCompanyCommission() { return companyCommission; }
    public void setCompanyCommission(BigDecimal companyCommission) { this.companyCommission = companyCommission; }
 
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
 
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}