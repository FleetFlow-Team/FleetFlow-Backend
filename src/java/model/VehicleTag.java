package model;
 
public class VehicleTag {
    private int vehicleId;
    private int tagId;
 
    public VehicleTag() {}
 
    public VehicleTag(int vehicleId, int tagId) {
        this.vehicleId = vehicleId;
        this.tagId = tagId;
    }
 
    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }
 
    public int getTagId() { return tagId; }
    public void setTagId(int tagId) { this.tagId = tagId; }
}
 