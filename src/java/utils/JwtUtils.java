package utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;

public class JwtUtils {

    // Khóa mã hóa chuẩn tối thiểu 256-bit chống WeakKeyException
    private static final String SECRET_STRING = "FleetFlowProjectSuperSecretKey2026SecureBridgesString";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());

    private static final long ACCESS_TOKEN_EXPIRATION = 864000000L;  // 1 ngày
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 ngày

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
}