/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;
/**
 *
 * @author asus
 */
public class PricingRuleDAO {
    public List<Map<String, Object>> getPricingRules() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM PricingRule";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ruleId", rs.getInt("RuleID"));
                map.put("vehicleTypeId", rs.getInt("VehicleTypeID"));
                map.put("bookingType", rs.getString("BookingType"));
                map.put("tripDirection", rs.getString("TripDirection"));
                map.put("basePrice", rs.getBigDecimal("BasePrice"));
                map.put("pricePerKm", rs.getBigDecimal("PricePerKm"));
                map.put("pricePerHour", rs.getBigDecimal("PricePerHour"));
                map.put("pricePerDay", rs.getBigDecimal("PricePerDay"));
                map.put("weekendMultiplier", rs.getBigDecimal("WeekendMultiplier"));
                list.add(map);
            }
        }
        return list;
    }

    public boolean updatePricingRule(int ruleId, BigDecimal basePrice, BigDecimal pricePerKm, BigDecimal pricePerHour, BigDecimal pricePerDay, BigDecimal weekendMultiplier) throws Exception {
        String sql = "UPDATE PricingRule SET BasePrice = ?, PricePerKm = ?, PricePerHour = ?, PricePerDay = ?, WeekendMultiplier = ? WHERE RuleID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, basePrice);
            ps.setBigDecimal(2, pricePerKm);
            ps.setBigDecimal(3, pricePerHour);
            ps.setBigDecimal(4, pricePerDay);
            ps.setBigDecimal(5, weekendMultiplier);
            ps.setInt(6, ruleId);
            return ps.executeUpdate() > 0;
        }
    }
}
