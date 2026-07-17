package dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import model.BookingExtension;
import utils.DbUtils;

/**
 * DAO cho bảng BookingExtension — dùng chung cho Luồng 1 (REQUESTED, xin
 * trước) và Luồng 2 (RETROACTIVE, chốt sổ quá giờ). Mọi thao tác đổi trạng
 * thái đều viết dạng "UPDATE ... WHERE <trạng thái cũ mong đợi>" để tự
 * idempotent — bấm 2 lần / 2 request cùng lúc chỉ 1 cái có tác dụng, cái sau
 * trả về 0 dòng ảnh hưởng, KHÔNG throw lỗi, để Service tự quyết định coi đó
 * là "đã xử lý rồi, bỏ qua" thay vì lỗi hệ thống.
 */
public class BookingExtensionDAO {

    private BookingExtension mapRow(ResultSet rs) throws SQLException {
        BookingExtension e = new BookingExtension();
        e.setId(rs.getInt("ExtensionID"));
        e.setBookingId(rs.getInt("BookingID"));
        e.setExtensionType(rs.getString("ExtensionType"));
        e.setRequestedByRole(rs.getString("RequestedByRole"));
        int reqBy = rs.getInt("RequestedByAccountId");
        e.setRequestedByAccountId(rs.wasNull() ? null : reqBy);
        e.setCounterpartyRole(rs.getString("CounterpartyRole"));
        e.setOldReturnTime(rs.getTimestamp("OldReturnTime"));
        e.setNewReturnTime(rs.getTimestamp("NewReturnTime"));
        e.setExtraMinutes(rs.getInt("ExtraMinutes"));
        e.setExtraAmount(rs.getBigDecimal("ExtraAmount"));
        e.setCounterpartyStatus(rs.getString("CounterpartyStatus"));
        int cRespBy = rs.getInt("CounterpartyRespondedBy");
        e.setCounterpartyRespondedBy(rs.wasNull() ? null : cRespBy);
        e.setCounterpartyRespondedAt(rs.getTimestamp("CounterpartyRespondedAt"));
        e.setDispatcherStatus(rs.getString("DispatcherStatus"));
        int dRespBy = rs.getInt("DispatcherRespondedBy");
        e.setDispatcherRespondedBy(rs.wasNull() ? null : dRespBy);
        e.setDispatcherRespondedAt(rs.getTimestamp("DispatcherRespondedAt"));
        e.setStatus(rs.getString("Status"));
        e.setRequestedAt(rs.getTimestamp("RequestedAt"));
        e.setExpiresAt(rs.getTimestamp("ExpiresAt"));
        e.setResolvedAt(rs.getTimestamp("ResolvedAt"));
        e.setNotes(rs.getString("Notes"));
        return e;
    }

    // ===================== Luồng 1 — REQUESTED =====================

    /**
     * Tạo 1 yêu cầu gia hạn PENDING, chờ 2 phiếu (đối xứng + dispatcher).
     * Nếu đang có 1 request PENDING khác cho cùng booking, filtered unique
     * index ở DB (UX_BookingExtension_OnePending) sẽ chặn — bắt lỗi và ném
     * lại thành IllegalArgumentException dễ hiểu cho Service/Controller.
     */
    public int createRequested(int bookingId, String requestedByRole, int requestedByAccountId,
            String counterpartyRole, Timestamp oldReturnTime, Timestamp newReturnTime,
            int extraMinutes, BigDecimal extraAmount, int pendingWindowMinutes) throws Exception {
        String sql = "INSERT INTO BookingExtension "
                + "(BookingID, ExtensionType, RequestedByRole, RequestedByAccountId, CounterpartyRole, "
                + "OldReturnTime, NewReturnTime, ExtraMinutes, ExtraAmount, "
                + "CounterpartyStatus, DispatcherStatus, Status, RequestedAt, ExpiresAt) "
                + "VALUES (?, 'REQUESTED', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'PENDING', 'PENDING', GETDATE(), DATEADD(MINUTE, ?, GETDATE()))";
        try (Connection conn = DbUtils.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setString(2, requestedByRole);
            ps.setInt(3, requestedByAccountId);
            ps.setString(4, counterpartyRole);
            ps.setTimestamp(5, oldReturnTime);
            ps.setTimestamp(6, newReturnTime);
            ps.setInt(7, extraMinutes);
            ps.setBigDecimal(8, extraAmount);
            ps.setInt(9, pendingWindowMinutes);
            try {
                ps.executeUpdate();
            } catch (SQLException sqlEx) {
                // Filtered unique index vi phạm -> đang có 1 PENDING khác cho booking này rồi
                if (sqlEx.getMessage() != null && sqlEx.getMessage().toLowerCase().contains("unique")) {
                    throw new IllegalArgumentException(
                            "Booking này đang có 1 yêu cầu gia hạn khác chờ xử lý. Vui lòng đợi yêu cầu đó xong.");
                }
                throw sqlEx;
            }
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Phiếu của bên "đối xứng" (Customer hoặc Driver, tùy CounterpartyRole).
     * APPROVE: chỉ cập nhật CounterpartyStatus, Status tổng do Service quyết
     * định tiếp (còn phải chờ Dispatcher). REJECT: chốt luôn Status=REJECTED
     * ngay lập tức, không cần đợi Dispatcher — đúng luật "1 phiếu chống là hỏng".
     * Trả về true nếu update có hiệu lực (false = đã bị xử lý trước đó rồi).
     */
    public boolean respondCounterparty(int extensionId, int accountId, boolean approve) throws Exception {
        String sql = approve
                ? "UPDATE BookingExtension SET CounterpartyStatus='APPROVED', "
                + "CounterpartyRespondedBy=?, CounterpartyRespondedAt=GETDATE() "
                + "WHERE ExtensionID=? AND Status='PENDING' AND CounterpartyStatus='PENDING'"
                : "UPDATE BookingExtension SET CounterpartyStatus='REJECTED', "
                + "CounterpartyRespondedBy=?, CounterpartyRespondedAt=GETDATE(), "
                + "Status='REJECTED', ResolvedAt=GETDATE() "
                + "WHERE ExtensionID=? AND Status='PENDING' AND CounterpartyStatus='PENDING'";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, extensionId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Phiếu của Dispatcher — kiểu broadcast, BẤT KỲ dispatcher nào bấm trước
     * thì tính (giống pattern DriverJobBroadcast "ai accept trước thắng"),
     * ai bấm sau vào cùng request đã bị xử lý sẽ nhận false, không lỗi.
     */
    public boolean respondDispatcher(int extensionId, int dispatcherAccountId, boolean approve) throws Exception {
        String sql = approve
                ? "UPDATE BookingExtension SET DispatcherStatus='APPROVED', "
                + "DispatcherRespondedBy=?, DispatcherRespondedAt=GETDATE() "
                + "WHERE ExtensionID=? AND Status='PENDING' AND DispatcherStatus='PENDING'"
                : "UPDATE BookingExtension SET DispatcherStatus='REJECTED', "
                + "DispatcherRespondedBy=?, DispatcherRespondedAt=GETDATE(), "
                + "Status='REJECTED', ResolvedAt=GETDATE() "
                + "WHERE ExtensionID=? AND Status='PENDING' AND DispatcherStatus='PENDING'";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dispatcherAccountId);
            ps.setInt(2, extensionId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Chốt Status tổng = APPROVED — chỉ thành công khi CẢ 2 phiếu đã APPROVED
     * và Status vẫn đang PENDING. Tự idempotent: nếu 2 luồng gọi cùng lúc
     * (VD counterparty vừa approve thấy dispatcher đã approve trước đó, và
     * ngược lại) chỉ 1 trong 2 thật sự finalize — bên còn lại nhận false,
     * Service dựa vào đó để KHÔNG chạy lặp phần dời giờ/cộng tiền.
     */
    public boolean finalizeApproved(Connection conn, int extensionId) throws SQLException {
        String sql = "UPDATE BookingExtension SET Status='APPROVED', ResolvedAt=GETDATE() "
                + "WHERE ExtensionID=? AND Status='PENDING' "
                + "AND CounterpartyStatus='APPROVED' AND DispatcherStatus='APPROVED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, extensionId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Hết 10 phút mà chưa đủ 2 phiếu đồng ý -> EXPIRED. Gọi lazy (khi có ai
     * đó động vào request này) chứ không cần scheduler riêng.
     */
    public boolean expireIfOverdue(int extensionId) throws Exception {
        String sql = "UPDATE BookingExtension SET Status='EXPIRED', ResolvedAt=GETDATE() "
                + "WHERE ExtensionID=? AND Status='PENDING' AND ExpiresAt < GETDATE()";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, extensionId);
            return ps.executeUpdate() > 0;
        }
    }

    // ===================== Luồng 2 — RETROACTIVE =====================

    /**
     * Ghi nhận 1 bản quá giờ hồi tố — luôn APPROVED ngay lúc tạo, không ai
     * duyệt/từ chối được (chuyện đã xảy ra rồi). Chạy trong transaction có
     * sẵn (conn) vì luôn đi kèm updateReturnTime + addToEstimatedTotal.
     */
    public int createRetroactive(Connection conn, int bookingId, Timestamp oldReturnTime,
            Timestamp newReturnTime, int extraMinutes, BigDecimal extraAmount, String notes) throws SQLException {
        String sql = "INSERT INTO BookingExtension "
                + "(BookingID, ExtensionType, OldReturnTime, NewReturnTime, ExtraMinutes, ExtraAmount, "
                + "CounterpartyStatus, DispatcherStatus, Status, RequestedAt, ResolvedAt, Notes) "
                + "VALUES (?, 'RETROACTIVE', ?, ?, ?, ?, 'NOT_REQUIRED', 'NOT_REQUIRED', 'APPROVED', GETDATE(), GETDATE(), ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setTimestamp(2, oldReturnTime);
            ps.setTimestamp(3, newReturnTime);
            ps.setInt(4, extraMinutes);
            ps.setBigDecimal(5, extraAmount);
            ps.setString(6, notes);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ===================== Truy vấn chung =====================

    public BookingExtension getById(int extensionId) throws Exception {
        String sql = "SELECT * FROM BookingExtension WHERE ExtensionID = ?";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, extensionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public BookingExtension getPendingByBookingId(int bookingId) throws Exception {
        String sql = "SELECT * FROM BookingExtension WHERE BookingID = ? AND Status = 'PENDING'";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public java.util.List<BookingExtension> getHistoryByBookingId(int bookingId) throws Exception {
        java.util.List<BookingExtension> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM BookingExtension WHERE BookingID = ? ORDER BY RequestedAt ASC";
        try (Connection conn = DbUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Tổng số phút đã "dùng" của ngân sách gộp chung (Luồng 1 APPROVED +
     * Luồng 2 RETROACTIVE, RETROACTIVE luôn APPROVED sẵn) — dùng để so với
     * trần cứng (2h HOURLY / 1 ngày DAILY) mỗi khi có request Luồng 1 mới,
     * và để tính cap động của Luồng 2 (xem BookingExtensionService).
     */
    public int getUsedBudgetMinutes(Connection conn, int bookingId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(ExtraMinutes), 0) AS UsedMinutes "
                + "FROM BookingExtension WHERE BookingID = ? AND Status = 'APPROVED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("UsedMinutes") : 0;
            }
        }
    }

    public int getUsedBudgetMinutes(int bookingId) throws Exception {
        try (Connection conn = DbUtils.getConnection()) {
            return getUsedBudgetMinutes(conn, bookingId);
        }
    }
}