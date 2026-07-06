package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/**
 * Helper chuẩn hoá response JSON cho các servlet:
 *  - set content-type + UTF-8 + CORS một chỗ (thay cho hàm prepare() lặp lại)
 *  - khuôn thống nhất: {"success":true,"data":...} / {"success":false,"error":...}
 *
 * Mục tiêu: giảm code lặp và thống nhất format lỗi. Áp dụng cho code MỚI hoặc
 * khi refactor; KHÔNG bắt buộc đổi ngay các controller cũ mà FE đang phụ thuộc.
 */
public final class ApiResponse {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ApiResponse() {
    }

    /** Set header JSON + UTF-8 + CORS. Gọi đầu mỗi request (kể cả doOptions). */
    public static void prepare(HttpServletResponse resp) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    /** Ghi một object bất kỳ ra JSON với HTTP status cho trước. */
    public static void write(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        PrintWriter out = resp.getWriter();
        out.print(GSON.toJson(body));
        out.flush();
    }

    /** {"success":true,"data":data} + 200 OK. */
    public static void success(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        m.put("data", data);
        write(resp, HttpServletResponse.SC_OK, m);
    }

    /** {"success":true} + 200 OK. */
    public static void ok(HttpServletResponse resp) throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        write(resp, HttpServletResponse.SC_OK, m);
    }

    /** {"success":false,"error":message} + status tuỳ chọn. */
    public static void error(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put("success", false);
        m.put("error", message);
        write(resp, status, m);
    }
}
