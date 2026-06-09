package model;

import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

public class Vehicle extends BaseEntity implements IAuditableEntity {

    private Long vehicleTypeId;
    private String licensePlate;
    private String chassisNumber;
    private String engineNumber;
    private String brand;
    private String model;
    private Integer seatCount;
    private String status;
    private Integer accumulatedKm;
    private Long createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String description;

    public Vehicle() {
    }

    public Vehicle(Long vehicleTypeId, String licensePlate, String chassisNumber,
            String engineNumber, String brand, String model, Integer seatCount,
            String status, Integer accumulatedKm, Long createdBy, Timestamp createdAt, String description) {
        this.vehicleTypeId = vehicleTypeId;
        this.licensePlate = licensePlate;
        this.chassisNumber = chassisNumber;
        this.engineNumber = engineNumber;
        this.brand = brand;
        this.model = model;
        this.seatCount = seatCount;
        this.status = status;
        this.accumulatedKm = accumulatedKm;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.description = description;
    }

    public Long getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(Long vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getChassisNumber() {
        return chassisNumber;
    }

    public void setChassisNumber(String chassisNumber) {
        this.chassisNumber = chassisNumber;
    }

    public String getEngineNumber() {
        return engineNumber;
    }

    public void setEngineNumber(String engineNumber) {
        this.engineNumber = engineNumber;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAccumulatedKm() {
        return accumulatedKm;
    }

    public void setAccumulatedKm(Integer accumulatedKm) {
        this.accumulatedKm = accumulatedKm;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
