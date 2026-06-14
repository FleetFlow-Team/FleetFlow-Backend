package model;

/**
 * Model phục vụ BE-5 (danh sách xe đặt được) và BE-6 (chi tiết 1 xe).
 * Gộp thông tin từ bảng Vehicle + VehicleType (TypeName) + danh sách Tag.
 */
public class Vehicle {

    private int vehicleId;
    private int vehicleTypeId;
    private String typeName;        // VehicleType.TypeName, vd "Xe 7 chỗ"
    private String licensePlate;
    private String chassisNumber;
    private String engineNumber;
    private String brand;
    private String model;
    private int seatCount;
    private String status;          // Available / OnTrip / Maintenance / Unavailable
    private Integer accumulatedKm;  // có thể NULL
    private String description;
    private String tags;            // gộp tag: "Ghế da, Êm ái, Phù hợp gia đình"

    public Vehicle() {
    }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }

    public int getVehicleTypeId() { return vehicleTypeId; }
    public void setVehicleTypeId(int vehicleTypeId) { this.vehicleTypeId = vehicleTypeId; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getChassisNumber() { return chassisNumber; }
    public void setChassisNumber(String chassisNumber) { this.chassisNumber = chassisNumber; }

    public String getEngineNumber() { return engineNumber; }
    public void setEngineNumber(String engineNumber) { this.engineNumber = engineNumber; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAccumulatedKm() { return accumulatedKm; }
    public void setAccumulatedKm(Integer accumulatedKm) { this.accumulatedKm = accumulatedKm; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
}
