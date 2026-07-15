/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;
/**
 *
 * @author asus
 */
public class HolidayDAO {
    public List<Map<String, Object>> getHolidays() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM Holiday ORDER BY HolidayDate";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("holidayId", rs.getInt("HolidayID"));
                map.put("holidayDate", rs.getDate("HolidayDate").toString());
                map.put("description", rs.getString("Description"));
                list.add(map);
            }
        }
        return list;
    }

    public boolean addHoliday(String holidayDate, String description) throws Exception {
        String sql = "INSERT INTO Holiday (HolidayDate, Description) VALUES (?, ?)";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, holidayDate);
            ps.setString(2, description);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteHoliday(int holidayId) throws Exception {
        String sql = "DELETE FROM Holiday WHERE HolidayID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, holidayId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Kiểm tra 1 ngày (java.sql.Date) có phải Ngày Lễ do Admin cấu hình hay không — dùng để tính phụ phí. */
    public boolean isHoliday(java.sql.Date date) throws Exception {
        String sql = "SELECT 1 FROM Holiday WHERE HolidayDate = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
