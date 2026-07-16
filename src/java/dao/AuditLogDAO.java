package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

public class AuditLogDAO {

    /**
     * Ghi 1 log hành động — dùng connection được truyền vào để gộp transaction
     * với hành động chính (ví dụ: update Booking.Status + ghi log cùng 1 transaction).
     *
     * accountId = null → hành động do HỆ THỐNG tự động thực hiện (scheduler, cron...),
     * KHÔNG được gán bừa cho 1 AccountID có sẵn (dù là 1) vì ID đó có thể đang
     * thuộc về 1 user thật, gây audit log ghi sai nhân sự.
     */
    public void log(Connection conn, Integer accountId, String action, String entityName,
            String entityId, String oldValue, String newValue, String ipAddress) throws Exception {

        String sql = "INSERT INTO AuditLog "
                + "(AccountID, Action, EntityName, EntityID, OldValue, NewValue, IpAddress, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (accountId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, accountId);
            }
            ps.setString(2, action);
            ps.setString(3, entityName);
            ps.setString(4, entityId);
            ps.setString(5, oldValue);
            ps.setString(6, newValue);
            ps.setString(7, ipAddress);
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * Overload tự mở/đóng connection riêng — dùng khi không cần gộp transaction.
     */
    public void log(Integer accountId, String action, String entityName,
            String entityId, String oldValue, String newValue, String ipAddress) throws Exception {
        try (Connection conn = DbUtils.getConnection()) {
            log(conn, accountId, action, entityName, entityId, oldValue, newValue, ipAddress);
        }
    }

    /**
     * BE-61: GET /api/v1/admin/audit-log — Xem nhật ký kiểm toán hệ thống (BR-20)
     * Tất cả filter đều optional (truyền null/empty = không áp dụng).
     * page bắt đầu từ 1.
     */
    public List<Map<String, Object>> findAll(String action, String entityName, Integer accountId,
            String fromDate, String toDate, int page, int pageSize) throws Exception {

        List<Map<String, Object>> list = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT al.AuditID, al.AccountID, acc.Email, acc.FullName, al.Action, "
              + "al.EntityName, al.EntityID, al.OldValue, al.NewValue, al.IpAddress, al.CreatedAt "
              + "FROM AuditLog al "
              + "LEFT JOIN Account acc ON acc.AccountID = al.AccountID "
              + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (action != null && !action.trim().isEmpty()) {
            sql.append("AND al.Action = ? ");
            params.add(action.trim());
        }
        if (entityName != null && !entityName.trim().isEmpty()) {
            sql.append("AND al.EntityName = ? ");
            params.add(entityName.trim());
        }
        if (accountId != null) {
            sql.append("AND al.AccountID = ? ");
            params.add(accountId);
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND al.CreatedAt >= ? ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND al.CreatedAt <= ? ");
            params.add(toDate.trim());
        }

        sql.append("ORDER BY al.CreatedAt DESC, al.AuditID DESC ")
           .append("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");

        int safePage = page < 1 ? 1 : page;
        int safeSize = pageSize < 1 ? 50 : pageSize;

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) {
                    ps.setInt(idx++, (Integer) p);
                } else {
                    ps.setString(idx++, (String) p);
                }
            }
            ps.setInt(idx++, (safePage - 1) * safeSize);
            ps.setInt(idx, safeSize);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("auditLogId", rs.getLong("AuditID"));
                    // getInt() trả về 0 khi cột NULL — dùng getObject để giữ đúng null,
                    // tránh FE hiểu nhầm accountId=0 là 1 account có thật.
                    Object accId = rs.getObject("AccountID");
                    m.put("accountId", accId); // null khi hành động do hệ thống tự động thực hiện
                    m.put("email", rs.getString("Email"));
                    m.put("fullName", accId == null ? "Hệ thống" : rs.getString("FullName"));
                    m.put("action", rs.getString("Action"));
                    m.put("entityName", rs.getString("EntityName"));
                    m.put("entityId", rs.getString("EntityID"));
                    m.put("oldValue", rs.getString("OldValue"));
                    m.put("newValue", rs.getString("NewValue"));
                    m.put("ipAddress", rs.getString("IpAddress"));
                    Timestamp ts = rs.getTimestamp("CreatedAt");
                    m.put("createdAt", ts == null ? null : ts.toString());
                    list.add(m);
                }
            }
        }
        return list;
    }

    public int countAll(String action, String entityName, Integer accountId,
            String fromDate, String toDate) throws Exception {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS Cnt FROM AuditLog al WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (action != null && !action.trim().isEmpty()) {
            sql.append("AND al.Action = ? ");
            params.add(action.trim());
        }
        if (entityName != null && !entityName.trim().isEmpty()) {
            sql.append("AND al.EntityName = ? ");
            params.add(entityName.trim());
        }
        if (accountId != null) {
            sql.append("AND al.AccountID = ? ");
            params.add(accountId);
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND al.CreatedAt >= ? ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND al.CreatedAt <= ? ");
            params.add(toDate.trim());
        }

        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer) {
                    ps.setInt(idx++, (Integer) p);
                } else {
                    ps.setString(idx++, (String) p);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Cnt");
                }
                return 0;
            }
        }
    }
}