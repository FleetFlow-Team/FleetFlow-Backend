package utils;

import java.io.File;
import javax.servlet.http.HttpServletRequest;

/**
 * Xác định đúng thư mục "Web Pages" (SOURCE, hiện trong Projects tree của
 * NetBeans) để lưu file upload lúc runtime — thay vì thư mục build/deploy.
 *
 * Lý do cần cái này: request.getServletContext().getRealPath("") trỏ vào
 * build/web/... (bản NetBeans copy ra để deploy chạy), KHÔNG phải thư mục
 * "web/" source thật. Ghi file vào build/web sẽ:
 *   - Mất trắng mỗi khi Clean and Build (Ant xóa nguyên thư mục build/)
 *   - Không nằm trong Git vì build/ luôn bị .gitignore
 *   -> Máy khác clone/pull code sẽ KHÔNG thấy ảnh.
 *
 * Cấu trúc chuẩn NetBeans Ant Java Web Application:
 *   ProjectRoot/
 *     src/java/...                     <- .java source
 *     web/                             <- "Web Pages" SOURCE (cần lưu vào đây)
 *     build/web/WEB-INF/classes/...    <- .class thực sự đang chạy (deploy copy)
 *
 * Servlet đang chạy nằm trong build/web/WEB-INF/classes, nên từ đó đi ngược
 * lên 4 cấp ra ProjectRoot, rồi trỏ sang thư mục "web" (anh em với "build").
 */
public class UploadUtils {

    private UploadUtils() {
    }

    /**
     * Trả về đường dẫn tuyệt đối tới thư mục "web" (source, hiện trong
     * Projects/Files tree của NetBeans). Nếu vì lý do nào đó không dò được
     * (deploy theo kiểu WAR đóng gói thật trên server khác, không chạy từ
     * NetBeans) thì fallback về getRealPath() như cũ — ảnh vẫn lưu được,
     * chỉ là không đảm bảo nằm trong source nữa.
     */
    public static String resolveSourceWebDir(HttpServletRequest request) {
        try {
            String classesPath = UploadUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            // classesPath dạng: .../build/web/WEB-INF/classes/
            File classesDir = new File(classesPath);
            File webInfDir = classesDir.getParentFile();   // WEB-INF
            File buildWebDir = webInfDir.getParentFile();  // build/web
            File buildDir = buildWebDir.getParentFile();   // build
            File projectRoot = buildDir.getParentFile();   // ProjectRoot

            File sourceWebDir = new File(projectRoot, "web");
            if (sourceWebDir.isDirectory()) {
                return sourceWebDir.getAbsolutePath();
            }
        } catch (Exception e) {
            System.out.println("[UploadUtils] Không dò được thư mục source 'web', dùng tạm realPath deploy: "
                    + e.getMessage());
        }
        return request.getServletContext().getRealPath("");
    }

    /**
     * Trả về đường dẫn thư mục deploy thật (build/web — nơi Tomcat đang serve
     * HTTP theo docBase, xem context.xml của server để confirm). Dùng để
     * đồng thời copy 1 bản ảnh qua đây, cho phép xem ảnh qua HTTP ngay lập
     * tức lúc dev mà không cần Clean & Build lại project.
     */
    public static String resolveDeployWebDir(HttpServletRequest request) {
        try {
            String classesPath = UploadUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            File classesDir = new File(classesPath);
            File webInfDir = classesDir.getParentFile();   // WEB-INF
            File buildWebDir = webInfDir.getParentFile();  // build/web — chính là deploy dir
            if (buildWebDir.isDirectory()) {
                return buildWebDir.getAbsolutePath();
            }
        } catch (Exception e) {
            System.out.println("[UploadUtils] Không dò được thư mục deploy 'build/web': " + e.getMessage());
        }
        return request.getServletContext().getRealPath("");
    }
}