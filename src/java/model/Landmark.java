package model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import model.base.BaseEntity;

/**
 * Điểm đến cố định (bến xe, sân bay...) để khách chọn nhanh khi đặt xe thay
 * vì tự ghim tọa độ tay. Tất cả landmark dùng chung bookingType = DISTANCE
 * (giống mọi điểm-tới-điểm tự do khác) — quy tắc tối thiểu 20km và cách tính
 * giá không đổi.
 *
 * LƯU Ý: KHÔNG cố phân loại landmark là "nội tỉnh"/"liên tỉnh" (INNER_CITY/
 * INTER_CITY), vì sau đợt sáp nhập tỉnh 2025 ranh giới hành chính đã thay
 * đổi rất lớn (VD Vũng Tàu giờ cùng tỉnh với Sài Gòn dù cách ~100km), và hệ
 * thống hiện không có dữ liệu ranh giới tỉnh (polygon) để tính chính xác —
 * so theo tên tỉnh dễ sai, so theo khoảng cách thì lại chính là DISTANCE
 * sẵn có rồi. Nếu sau này có nhu cầu phân loại lại, cần bổ sung dữ liệu địa
 * giới hành chính chuẩn (GeoJSON ranh giới tỉnh) chứ không nên đoán bằng tên.
 */
public class Landmark extends BaseEntity {

    private String name;            // "Bến xe Miền Tây", "Sân bay Tân Sơn Nhất"...
    private String address;         // Địa chỉ đầy đủ hiển thị cho khách
    private BigDecimal lat;
    private BigDecimal lng;
    private String category;        // BUS_STATION, AIRPORT, OTHER
    private int createdBy;
    private Timestamp createdAt;

    public Landmark() {
    }

    public Landmark(String name, String address, BigDecimal lat, BigDecimal lng,
            String category, int createdBy, Timestamp createdAt) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.category = category;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}