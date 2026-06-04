/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model.base;

import java.sql.Timestamp;

public interface IAuditableEntity {

    Timestamp getCreatedAt();

    void setCreatedAt(Timestamp createdAt);

    Timestamp getUpdatedAt();

    void setUpdatedAt(Timestamp updatedAt);
}
