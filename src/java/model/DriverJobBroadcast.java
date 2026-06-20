package model;

import java.sql.Timestamp;
import model.base.BaseEntity;

public class DriverJobBroadcast extends BaseEntity {

    private int bookingId;
    private int assignedDriverId;
    private int dispatchedBy;
    private String status;          // PENDING, DISPATCHED, ACCEPTED, REJECTED
    private Timestamp dispatchedAt;
    private Timestamp respondedAt;

    public DriverJobBroadcast() {
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getAssignedDriverId() {
        return assignedDriverId;
    }

    public void setAssignedDriverId(int assignedDriverId) {
        this.assignedDriverId = assignedDriverId;
    }

    public int getDispatchedBy() {
        return dispatchedBy;
    }

    public void setDispatchedBy(int dispatchedBy) {
        this.dispatchedBy = dispatchedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Timestamp dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Timestamp getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Timestamp respondedAt) {
        this.respondedAt = respondedAt;
    }
}