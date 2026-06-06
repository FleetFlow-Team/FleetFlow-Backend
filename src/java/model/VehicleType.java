/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

import model.base.BaseEntity;

/**
 *
 * @author User
 */
public class VehicleType extends BaseEntity {
    private String typeName;
    private Integer seatCount;
    private String description;
    
    //constructor

    public VehicleType() {
    }

    public VehicleType(String typeName, Integer seatCount, String description) {
        this.typeName = typeName;
        this.seatCount = seatCount;
        this.description = description;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
}
