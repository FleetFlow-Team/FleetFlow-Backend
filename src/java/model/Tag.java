package model;
 
import model.base.BaseEntity;
 
public class Tag extends BaseEntity {
    private String tagName;
    private String description;
 
    public Tag() {}
 
    public Tag(String tagName, String description) {
        this.tagName = tagName;
        this.description = description;
    }
 
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
 
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
