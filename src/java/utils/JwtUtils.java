package utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;

public class JwtUtils {

    // Khóa mã hóa chuẩn tối thiểu 256-bit chống WeakKeyException
    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());

    private static final long ACCESS_TOKEN_EXPIRATION = 900000L;    // 15 phút (900.000 ms)
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 ngày (604.800.000 ms)

    public static String generateAccessToken(String email, String roleName) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", roleName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    public static String generateRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // ===================== ĐỌC / XÁC THỰC TOKEN (bổ sung cho BE-3/4/7) =====================

    /** Giải mã và lấy toàn bộ claims; ném JwtException nếu token sai chữ ký hoặc hết hạn. */
    public static Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** true nếu token hợp lệ (đúng chữ ký + chưa hết hạn). */
    public static boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Lấy email (subject) từ token. Trả null nếu token không hợp lệ. */
    public static String getEmailFromToken(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Lấy role (claim "role") từ access token. Trả null nếu không có / không hợp lệ. */
    public static String getRoleFromToken(String token) {
        try {
            Object role = parseClaims(token).get("role");
            return role == null ? null : role.toString();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
