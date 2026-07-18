package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.DbUtils;

/**
 * DAO cho lịch sử thao tác trên đơn khiếu nại (bảng ComplaintAction) + các
 * update trạng thái Complaint có guard theo state machine:
 * PENDING -> IN_PROGRESS -> RESOLVED / CLOSED_UNRESOLVED.
 *
 * Mọi UPDATE trạng thái đều kèm điều kiện WHERE Status = trạng thái nguồn
 * và trả về rowcount — 2 dispatcher bấm gần như cùng lúc thì chỉ 1 người
 * thắng, người kia nhận false (không cần lock).
 */
public class ComplaintActionDAO {

    public void insertAction(int complaintId, int actorAccountId, String actionCode,
            String reasonCode, String customerMessage) throws Exception {
        String sql = "INSERT INTO ComplaintAction "
                + "(ComplaintID, ActorAccountID, ActionCode, ReasonCode, CustomerMessage, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, GETDATE())";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.setInt(2, actorAccountId);
            ps.setString(3, actionCode);
            if (reasonCode != null) {
                ps.setString(4, reasonCode);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, customerMessage);
            ps.executeUpdate();
        }
    }

    /**
     * Timeline cho KHÁCH xem (spec mục 7): chỉ trả thời gian + nội dung tự
     * sinh, KHÔNG trả ActorAccountID / thông tin dispatcher.
     */
    public List<Map<String, Object>> getTimelineForCustomer(int complaintId) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT CreatedAt, ActionCode, CustomerMessage FROM ComplaintAction "
                + "WHERE ComplaintID = ? ORDER BY CreatedAt ASC, ActionID ASC";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    Timestamp t = rs.getTimestamp("CreatedAt");
                    m.put("time", t != null ? t.toString() : null);
                    m.put("actionCode", rs.getString("ActionCode"));
                    m.put("message", rs.getString("CustomerMessage"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    /** Đếm số action theo prefix — vd 'CONTACT_DRIVER_%' để check rule
     *  "không được chốt đơn khi chưa có action nào" (rule 5). */
    public int countActionsByPrefix(int complaintId, String actionCodePrefix) throws Exception {
        String sql = "SELECT COUNT(*) FROM ComplaintAction WHERE ComplaintID = ? AND ActionCode LIKE ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.setString(2, actionCodePrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Đếm số action thuộc bộ 4 hành động xử lý OTHER (VERIFIED_HANDLED/
     *  CANNOT_VERIFY/ESCALATED/REJECTED) — dùng để check rule "không được
     *  chốt đơn khi chưa có hành động xử lý nào" (tương đương rule 5 của
     *  LOST_LUGGAGE nhưng áp dụng cho OTHER). */
    public int countHandleActions(int complaintId) throws Exception {
        String sql = "SELECT COUNT(*) FROM ComplaintAction WHERE ComplaintID = ? "
                + "AND ActionCode IN ('VERIFIED_HANDLED', 'CANNOT_VERIFY', 'ESCALATED', 'REJECTED')";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Đếm chính xác 1 action code — vd CONTACT_DRIVER_NO_RESPONSE để check
     *  rule "gọi hụt tối thiểu 3 lần mới được chốt DRIVER_UNREACHABLE". */
    public int countActionsByCode(int complaintId, String actionCode) throws Exception {
        String sql = "SELECT COUNT(*) FROM ComplaintAction WHERE ComplaintID = ? AND ActionCode = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.setString(2, actionCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Thông tin cốt lõi của đơn để service check guard trước khi thao tác. */
    public Map<String, Object> getComplaintCore(int complaintId) throws Exception {
        String sql = "SELECT ComplaintID, ComplaintType, Status, BookingID, CustomerID, CreatedAt "
                + "FROM Complaint WHERE ComplaintID = ? AND IsDeleted = 0";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("complaintId", rs.getInt("ComplaintID"));
                m.put("type", rs.getString("ComplaintType"));
                m.put("status", rs.getString("Status"));
                int bookingId = rs.getInt("BookingID");
                m.put("bookingId", rs.wasNull() ? null : bookingId);
                int customerId = rs.getInt("CustomerID");
                m.put("customerId", rs.wasNull() ? null : customerId);
                Timestamp c = rs.getTimestamp("CreatedAt");
                m.put("createdAt", c != null ? c.toString() : null);
                return m;
            }
        }
    }

    /** PENDING -> IN_PROGRESS + gán assignee. False nếu đơn không còn PENDING
     *  (đã có dispatcher khác nhận trước). */
    public boolean assignComplaint(int complaintId, int assigneeAccountId) throws Exception {
        String sql = "UPDATE Complaint SET Status = 'IN_PROGRESS', AssigneeID = ? "
                + "WHERE ComplaintID = ? AND IsDeleted = 0 AND Status = 'PENDING'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, assigneeAccountId);
            ps.setInt(2, complaintId);
            return ps.executeUpdate() > 0;
        }
    }

    /** IN_PROGRESS -> RESOLVED / CLOSED_UNRESOLVED. Resolution lưu đúng nội
     *  dung tự sinh khách nhìn thấy (không có text tự do). */
    public boolean closeComplaint(int complaintId, String outcome, String reasonCode,
            String customerMessage) throws Exception {
        String sql = "UPDATE Complaint SET Status = ?, ReasonCode = ?, Resolution = ?, ResolvedAt = GETDATE() "
                + "WHERE ComplaintID = ? AND IsDeleted = 0 AND Status = 'IN_PROGRESS'";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outcome);
            if (reasonCode != null) {
                ps.setString(2, reasonCode);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, customerMessage);
            ps.setInt(4, complaintId);
            return ps.executeUpdate() > 0;
        }
    }

    /** CustomerID chủ đơn — để verify quyền xem timeline. -1 nếu đơn của guest. */
    public int getOwnerCustomerId(int complaintId) throws Exception {
        String sql = "SELECT CustomerID FROM Complaint WHERE ComplaintID = ? AND IsDeleted = 0";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("CustomerID");
                    return rs.wasNull() ? -1 : id;
                }
                return -1;
            }
        }
    }
}