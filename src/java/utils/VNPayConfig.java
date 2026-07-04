package utils;

import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc cấu hình VNPay từ /config/vnpay.properties trên classpath.
 * Đổi môi trường (sandbox → production, URL FE...) chỉ cần sửa file properties,
 * không sửa code.
 */
public class VNPayConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = VNPayConfig.class.getResourceAsStream("/config/vnpay.properties")) {
            if (in != null) {
                PROPS.load(in);
            } else {
                System.err.println("Khong tim thay /config/vnpay.properties tren classpath");
            }
        } catch (Exception e) {
            System.err.println("Loi doc vnpay.properties: " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }
}
