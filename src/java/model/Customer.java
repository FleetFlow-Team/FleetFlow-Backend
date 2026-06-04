/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;

import java.sql.Timestamp;
import model.base.BaseEntity;
import model.base.IAuditableEntity;

/**
 *
 * @author User
 */
public class Customer extends BaseEntity implements IAuditableEntity {
    private Timestamp createdAt;
    private Timestamp updatedAt;
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
