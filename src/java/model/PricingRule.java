package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;
 
public class PricingRule extends BaseEntity {
    private int vehicleTypeId;
    private String bookingType;      // HOURLY, DAILY, DISTANCE, INNER_CITY, INTER_CITY
    private String tripDirection;    // ONE_WAY, ROUND_TRIP
    private BigDecimal pricePerKm;
    private BigDecimal pricePerHour;
    private BigDecimal pricePerDay;
    private BigDecimal basePrice;
    private BigDecimal weekendMultiplier;
    private int updatedBy;
    private Timestamp updatedAt;
 
    public PricingRule() {}
 
    public PricingRule(int vehicleTypeId, String bookingType, String tripDirection,
                       BigDecimal pricePerKm, BigDecimal pricePerHour, BigDecimal pricePerDay,
                       BigDecimal basePrice, BigDecimal weekendMultiplier,
                       int updatedBy, Timestamp updatedAt) {
        this.vehicleTypeId = vehicleTypeId;
        this.bookingType = bookingType;
        this.tripDirection = tripDirection;
        this.pricePerKm = pricePerKm;
        this.pricePerHour = pricePerHour;
        this.pricePerDay = pricePerDay;
        this.basePrice = basePrice;
        this.weekendMultiplier = weekendMultiplier;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }
 
    public int getVehicleTypeId() { return vehicleTypeId; }
    public void setVehicleTypeId(int vehicleTypeId) { this.vehicleTypeId = vehicleTypeId; }
 
    public String getBookingType() { return bookingType; }
    public void setBookingType(String bookingType) { this.bookingType = bookingType; }
 
    public String getTripDirection() { return tripDirection; }
    public void setTripDirection(String tripDirection) { this.tripDirection = tripDirection; }
 
    public BigDecimal getPricePerKm() { return pricePerKm; }
    public void setPricePerKm(BigDecimal pricePerKm) { this.pricePerKm = pricePerKm; }
 
    public BigDecimal getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(BigDecimal pricePerHour) { this.pricePerHour = pricePerHour; }
 
    public BigDecimal getPricePerDay() { return pricePerDay; }
    public void setPricePerDay(BigDecimal pricePerDay) { this.pricePerDay = pricePerDay; }
 
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
 
    public BigDecimal getWeekendMultiplier() { return weekendMultiplier; }
    public void setWeekendMultiplier(BigDecimal weekendMultiplier) { this.weekendMultiplier = weekendMultiplier; }
 
    public int getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(int updatedBy) { this.updatedBy = updatedBy; }
 
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
 