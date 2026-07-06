package utils;

import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc cấu hình chung của ứng dụng từ /config/app.properties trên classpath
 * (API key của bên thứ 3: Gemini, VietMap...). Mục tiêu: KHÔNG hardcode key
 * trong file .java. Đổi key chỉ cần sửa file properties, không sửa code.
 *
 * File thật app.properties nằm trong .gitignore — copy từ app.properties.example
 * rồi điền key thật cho từng máy.
 */
public class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/config/app.properties")) {
            if (in != null) {
                PROPS.load(in);
            } else {
                System.err.println("Khong tim thay /config/app.properties tren classpath "
                        + "(copy tu app.properties.example va dien key)");
            }
        } catch (Exception e) {
            System.err.println("Loi doc app.properties: " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }
}
