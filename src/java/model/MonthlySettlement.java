package model;
 
import java.math.BigDecimal;
import java.sql.Timestamp;
 
public class MonthlySettlement {
    private int id;
    private int driverId;
    private Integer settlementMonth;
    private Integer settlementYear;
    private BigDecimal onlineTripsTotal;
    private BigDecimal cashTripsDebt;
    private BigDecimal penaltyBonus;
    private BigDecimal netPayout;
    private String status;
    private Timestamp settledAt;
 
    public MonthlySettlement() {}
 
    public MonthlySettlement(int driverId, Integer settlementMonth, Integer settlementYear,
                             BigDecimal onlineTripsTotal, BigDecimal cashTripsDebt,
                             BigDecimal penaltyBonus, BigDecimal netPayout, String status) {
        this.driverId = driverId;
        this.settlementMonth = settlementMonth;
        this.settlementYear = settlementYear;
        this.onlineTripsTotal = onlineTripsTotal;
        this.cashTripsDebt = cashTripsDebt;
        this.penaltyBonus = penaltyBonus;
        this.netPayout = netPayout;
        this.status = status;
    }
 
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
 
    public int getDriverId() { return driverId; }
    public void setDriverId(int driverId) { this.driverId = driverId; }
 
    public Integer getSettlementMonth() { return settlementMonth; }
    public void setSettlementMonth(Integer settlementMonth) { this.settlementMonth = settlementMonth; }
 
    public Integer getSettlementYear() { return settlementYear; }
    public void setSettlementYear(Integer settlementYear) { this.settlementYear = settlementYear; }
 
    public BigDecimal getOnlineTripsTotal() { return onlineTripsTotal; }
    public void setOnlineTripsTotal(BigDecimal onlineTripsTotal) { this.onlineTripsTotal = onlineTripsTotal; }
 
    public BigDecimal getCashTripsDebt() { return cashTripsDebt; }
    public void setCashTripsDebt(BigDecimal cashTripsDebt) { this.cashTripsDebt = cashTripsDebt; }
 
    public BigDecimal getPenaltyBonus() { return penaltyBonus; }
    public void setPenaltyBonus(BigDecimal penaltyBonus) { this.penaltyBonus = penaltyBonus; }
 
    public BigDecimal getNetPayout() { return netPayout; }
    public void setNetPayout(BigDecimal netPayout) { this.netPayout = netPayout; }
 
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
 
    public Timestamp getSettledAt() { return settledAt; }
    public void setSettledAt(Timestamp settledAt) { this.settledAt = settledAt; }
}