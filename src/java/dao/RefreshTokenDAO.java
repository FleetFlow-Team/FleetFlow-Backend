package dao;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import utils.DbUtils;

/**
 * Quản lý vòng đời của refreshToken trong DB.
 *
 * KHÔNG lưu refreshToken thô — chỉ lưu SHA-256 hash của nó, giống cách lưu
 * password. Nếu DB bị lộ, hacker cũng không lấy lại được token gốc để dùng.
 *
 * Mục đích chính: cho phép REVOKE (thu hồi) refreshToken bất cứ lúc nào
 * (logout, đổi mật khẩu, khóa tài khoản, phát hiện gian lận) — điều mà JWT
 * thuần (stateless) không tự làm được.
 */
public class RefreshTokenDAO {

    /**
     * Lưu 1 refreshToken mới vào DB (dạng hash) sau khi login/refresh thành công.
     */
    public void saveToken(int accountId, String rawRefreshToken, Date expiresAt) throws Exception {
        String tokenHash = sha256(rawRefreshToken);
        String sql = "INSERT INTO RefreshToken (AccountID, TokenHash, ExpiresAt, Revoked, CreatedAt) "
                + "VALUES (?, ?, ?, 0, GETDATE())";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, new Timestamp(expiresAt.getTime()));
            ps.executeUpdate();
        }
    }

    /**
     * Kiểm tra refreshToken còn "sống" trong DB không: tồn tại, chưa bị revoke,
     * chưa hết hạn (theo bản ghi DB, độc lập với hạn dùng trong chữ ký JWT).
     * Trả về AccountID nếu hợp lệ, -1 nếu không.
     */
    public int getValidAccountIdByToken(String rawRefreshToken) throws Exception {
        String tokenHash = sha256(rawRefreshToken);
        String sql = "SELECT AccountID FROM RefreshToken "
                + "WHERE TokenHash = ? AND Revoked = 0 AND ExpiresAt > GETDATE()";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("AccountID") : -1;
            }
        }
    }

    /**
     * Thu hồi (revoke) đúng 1 refreshToken cụ thể — dùng khi rotate token
     * (refresh xong thì token cũ phải chết ngay) hoặc khi logout 1 thiết bị.
     */
    public void revokeToken(String rawRefreshToken) throws Exception {
        String tokenHash = sha256(rawRefreshToken);
        String sql = "UPDATE RefreshToken SET Revoked = 1, RevokedAt = GETDATE() WHERE TokenHash = ?";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        }
    }

    /**
     * Thu hồi TOÀN BỘ refreshToken đang sống của 1 tài khoản — dùng khi:
     * đổi mật khẩu, Admin khóa tài khoản, hoặc user chọn "Đăng xuất khỏi mọi thiết bị".
     */
    public void revokeAllTokensForAccount(int accountId) throws Exception {
        String sql = "UPDATE RefreshToken SET Revoked = 1, RevokedAt = GETDATE() "
                + "WHERE AccountID = ? AND Revoked = 0";
        try (Connection conn = DbUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.executeUpdate();
        }
    }

    private String sha256(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}