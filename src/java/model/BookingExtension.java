package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;

/**
 * 1 yêu cầu gia hạn (Luồng 1 - REQUESTED) hoặc 1 bản ghi quá giờ hồi tố
 * (Luồng 2 - RETROACTIVE) của 1 booking HOURLY/DAILY.
 *
 * REQUESTED cần 2 phiếu đồng ý độc lập (CounterpartyStatus + DispatcherStatus)
 * mới APPROVED; RETROACTIVE luôn APPROVED ngay lúc tạo (ghi nhận việc đã xảy
 * ra, không ai duyệt/từ chối được).
 */
public class BookingExtension extends BaseEntity {

    private int bookingId;
    private String extensionType;   // REQUESTED, RETROACTIVE

    private String requestedByRole; // CUSTOMER, DRIVER (null cho RETROACTIVE)
    private Integer requestedByAccountId;
    private String counterpartyRole; // CUSTOMER hoặc DRIVER - bên phải duyệt

    private Timestamp oldReturnTime;
    private Timestamp newReturnTime;
    private int extraMinutes;
    private BigDecimal extraAmount;

    private String counterpartyStatus; // PENDING/APPROVED/REJECTED/NOT_REQUIRED
    private Integer counterpartyRespondedBy;
    private Timestamp counterpartyRespondedAt;

    private String dispatcherStatus; // PENDING/APPROVED/REJECTED/NOT_REQUIRED
    private Integer dispatcherRespondedBy;
    private Timestamp dispatcherRespondedAt;

    private String status; // PENDING/APPROVED/REJECTED/EXPIRED
    private Timestamp requestedAt;
    private Timestamp expiresAt;
    private Timestamp resolvedAt;
    private String notes;

    public BookingExtension() {
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public String getExtensionType() {
        return extensionType;
    }

    public void setExtensionType(String extensionType) {
        this.extensionType = extensionType;
    }

    public String getRequestedByRole() {
        return requestedByRole;
    }

    public void setRequestedByRole(String requestedByRole) {
        this.requestedByRole = requestedByRole;
    }

    public Integer getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Integer requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
    }

    public String getCounterpartyRole() {
        return counterpartyRole;
    }

    public void setCounterpartyRole(String counterpartyRole) {
        this.counterpartyRole = counterpartyRole;
    }

    public Timestamp getOldReturnTime() {
        return oldReturnTime;
    }

    public void setOldReturnTime(Timestamp oldReturnTime) {
        this.oldReturnTime = oldReturnTime;
    }

    public Timestamp getNewReturnTime() {
        return newReturnTime;
    }

    public void setNewReturnTime(Timestamp newReturnTime) {
        this.newReturnTime = newReturnTime;
    }

    public int getExtraMinutes() {
        return extraMinutes;
    }

    public void setExtraMinutes(int extraMinutes) {
        this.extraMinutes = extraMinutes;
    }

    public BigDecimal getExtraAmount() {
        return extraAmount;
    }

    public void setExtraAmount(BigDecimal extraAmount) {
        this.extraAmount = extraAmount;
    }

    public String getCounterpartyStatus() {
        return counterpartyStatus;
    }

    public void setCounterpartyStatus(String counterpartyStatus) {
        this.counterpartyStatus = counterpartyStatus;
    }

    public Integer getCounterpartyRespondedBy() {
        return counterpartyRespondedBy;
    }

    public void setCounterpartyRespondedBy(Integer counterpartyRespondedBy) {
        this.counterpartyRespondedBy = counterpartyRespondedBy;
    }

    public Timestamp getCounterpartyRespondedAt() {
        return counterpartyRespondedAt;
    }

    public void setCounterpartyRespondedAt(Timestamp counterpartyRespondedAt) {
        this.counterpartyRespondedAt = counterpartyRespondedAt;
    }

    public String getDispatcherStatus() {
        return dispatcherStatus;
    }

    public void setDispatcherStatus(String dispatcherStatus) {
        this.dispatcherStatus = dispatcherStatus;
    }

    public Integer getDispatcherRespondedBy() {
        return dispatcherRespondedBy;
    }

    public void setDispatcherRespondedBy(Integer dispatcherRespondedBy) {
        this.dispatcherRespondedBy = dispatcherRespondedBy;
    }

    public Timestamp getDispatcherRespondedAt() {
        return dispatcherRespondedAt;
    }

    public void setDispatcherRespondedAt(Timestamp dispatcherRespondedAt) {
        this.dispatcherRespondedAt = dispatcherRespondedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Timestamp requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Timestamp resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}