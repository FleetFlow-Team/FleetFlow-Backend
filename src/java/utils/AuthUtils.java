package utils;

import javax.servlet.http.HttpServletRequest;

/**
 * Tiện ích lấy danh tính người dùng từ JWT trong header Authorization.
 * Dùng cho các endpoint cần xác thực (BE-3 profile, BE-4 update, BE-7 bookings)
 * để KHÔNG nhận customerId từ client (đúng RBAC theo NFR-7.2).
 */
public class AuthUtils {

    /** Lấy access token từ header "Authorization: Bearer xxx". Trả null nếu thiếu. */
    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /** Email của người đăng nhập nếu token hợp lệ, ngược lại null (chưa đăng nhập / token hỏng/hết hạn). */
    public static String getEmailIfValid(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            return null;
        }
        return JwtUtils.getEmailFromToken(token);
    }

    /** Role của người đăng nhập (Customer/Driver/Dispatcher/Admin) nếu token hợp lệ, ngược lại null. */
    public static String getRoleIfValid(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null || !JwtUtils.validateToken(token)) {
            return null;
        }
        return JwtUtils.getRoleFromToken(token);
    }
}
