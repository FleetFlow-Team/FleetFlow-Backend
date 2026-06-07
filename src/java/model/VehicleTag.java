package model;
 
public class VehicleTag {
    private Long vehicleId;
    private Long tagId;
 
    public VehicleTag() {}
 
    public VehicleTag(Long vehicleId, Long tagId) {
        this.vehicleId = vehicleId;
        this.tagId = tagId;
    }
 
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
 
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
}
 