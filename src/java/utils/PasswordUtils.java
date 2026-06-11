package utils;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtils {

    /**
     * Dùng khi ĐĂNG KÝ hoặc QUÊN MẬT KHẨU:
     * Băm mật khẩu thô (plain) thành chuỗi bảo mật dạng $2a$10$...
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            return "";
        }
        // Số 10 ở đây chính là Work Factor để sinh ra số $10$ trong chuỗi của bạn
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /**
     * Dùng khi ĐĂNG NHẬP hoặc ĐỔI MẬT KHẨU:
     * Đối chiếu mật khẩu nhập tay xem có khớp với mã $2a$10$... dưới DB không
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}