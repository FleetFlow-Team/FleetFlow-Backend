package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import utils.DbUtils;

public class CustomerDAO {

    public Integer getCustomerIdByAccountId(int accountId) {

        String sql = "SELECT CustomerID FROM Customer WHERE AccountID = ?";

        try (
            Connection conn = DbUtils.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, accountId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("CustomerID");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}