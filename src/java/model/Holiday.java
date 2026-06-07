package model;
 
import java.sql.Date;
import model.base.BaseEntity;
 
public class Holiday extends BaseEntity {
    private Date holidayDate;
    private String description;
 
    public Holiday() {}
 
    public Holiday(Date holidayDate, String description) {
        this.holidayDate = holidayDate;
        this.description = description;
    }
 
    public Date getHolidayDate() { return holidayDate; }
    public void setHolidayDate(Date holidayDate) { this.holidayDate = holidayDate; }
 
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}