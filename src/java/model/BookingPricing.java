package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;

public class BookingPricing extends BaseEntity {
    private int bookingId;
    // ĐÃ BỎ driverId — tránh 2 nguồn sự thật xung đột.
    // Muốn biết driver nào nhận chuyến, query DriverJobBroadcast WHERE BookingID=? AND Status='ACCEPTED'
    private int ruleId;
    private BigDecimal baseFare;
    private BigDecimal weekendSurcharge;
    private BigDecimal discountAmount;
    private BigDecimal estimatedTotal;
    private int approvedBy;
    private Timestamp approvedAt;
    // Snapshot PricePerHour/PricePerDay của vehicleType này TẠI THỜI ĐIỂM đặt xe —
    // dùng cho gia hạn/quá giờ sau này, KHÔNG tra lại PricingRule sống vì bảng đó
    // có thể bị Admin sửa giá sau khi khách đã đặt (update in-place, không versioning).
    private BigDecimal pricePerHourSnapshot;
    private BigDecimal pricePerDaySnapshot;

    public BookingPricing() {}

    public BookingPricing(int bookingId, int ruleId,
                          BigDecimal baseFare, BigDecimal weekendSurcharge,
                          BigDecimal discountAmount, BigDecimal estimatedTotal,
                          int approvedBy, Timestamp approvedAt) {
        this.bookingId = bookingId;
        this.ruleId = ruleId;
        this.baseFare = baseFare;
        this.weekendSurcharge = weekendSurcharge;
        this.discountAmount = discountAmount;
        this.estimatedTotal = estimatedTotal;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public int getRuleId() { return ruleId; }
    public void setRuleId(int ruleId) { this.ruleId = ruleId; }

    public BigDecimal getBaseFare() { return baseFare; }
    public void setBaseFare(BigDecimal baseFare) { this.baseFare = baseFare; }

    public BigDecimal getWeekendSurcharge() { return weekendSurcharge; }
    public void setWeekendSurcharge(BigDecimal weekendSurcharge) { this.weekendSurcharge = weekendSurcharge; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getEstimatedTotal() { return estimatedTotal; }
    public void setEstimatedTotal(BigDecimal estimatedTotal) { this.estimatedTotal = estimatedTotal; }

    public int getApprovedBy() { return approvedBy; }
    public void setApprovedBy(int approvedBy) { this.approvedBy = approvedBy; }

    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }

    public BigDecimal getPricePerHourSnapshot() { return pricePerHourSnapshot; }
    public void setPricePerHourSnapshot(BigDecimal pricePerHourSnapshot) { this.pricePerHourSnapshot = pricePerHourSnapshot; }

    public BigDecimal getPricePerDaySnapshot() { return pricePerDaySnapshot; }
    public void setPricePerDaySnapshot(BigDecimal pricePerDaySnapshot) { this.pricePerDaySnapshot = pricePerDaySnapshot; }
}