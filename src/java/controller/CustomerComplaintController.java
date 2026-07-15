package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ComplaintDAO;
import dao.ComplaintDAO.ComplaintForm;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/complaints")
public class CustomerComplaintController extends HttpServlet {

    private final ComplaintDAO dao = new ComplaintDAO();
    private final Gson gson = new Gson();

    private static final int RATE_LIMIT_WINDOW_MINUTES = 5;
    private static final int RATE_LIMIT_MAX_REQUESTS = 3;

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepare(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        prepare(response);
        Map<String, Object> res = new HashMap<>();
        try {
            JsonObject body = readBody(request);

            String type = getStr(body, "type");
            if (type == null) {
                fail(response, res, 400, "Thiếu 'type'. Chấp nhận: LOST_LUGGAGE, SERVICE_FEEDBACK, OTHER.");
                return;
            }
            type = type.toUpperCase();
            if (!type.equals("LOST_LUGGAGE") && !type.equals("SERVICE_FEEDBACK") && !type.equals("OTHER")) {
                fail(response, res, 400, "type không hợp lệ. Chấp nhận: LOST_LUGGAGE, SERVICE_FEEDBACK, OTHER.");
                return;
            }

            ComplaintForm f = new ComplaintForm();
            f.type = type;
            f.region = normalizeRegion(getStr(body, "region"));
            f.fullName = getStr(body, "fullName");
            f.email = getStr(body, "email");
            f.phone = getStr(body, "phone");
            f.province = getStr(body, "province");
            f.issueType = getStr(body, "issueType");
            f.fromLocation = getStr(body, "fromLocation");
            f.toLocation = getStr(body, "toLocation");
            f.content = getStr(body, "content");

            if (body.has("fare") && !body.get("fare").isJsonNull()) {
                try {
                    f.fare = body.get("fare").getAsBigDecimal();
                } catch (Exception ignore) {
                }
            }
            if (body.has("boardingTime") && !body.get("boardingTime").isJsonNull()) {
                String bt = body.get("boardingTime").getAsString().trim();
                if (!bt.isEmpty()) {
                    try {
                        f.boardingTime = Timestamp.valueOf(bt.replace("T", " "));
                    } catch (Exception ignore) {
                    }
                }
            }
            if (body.has("bookingId") && !body.get("bookingId").isJsonNull()) {
                try {
                    f.bookingId = body.get("bookingId").getAsInt();
                } catch (Exception ignore) {
                }
            }
            if (body.has("customerId") && !body.get("customerId").isJsonNull()) {
                try {
                    f.customerId = body.get("customerId").getAsInt();
                } catch (Exception ignore) {
                }
            }

            if (f.fullName == null) {
                fail(response, res, 400, "Thiếu họ tên.");
                return;
            }
            if (f.phone == null && f.email == null) {
                fail(response, res, 400, "Cần ít nhất số điện thoại hoặc email để liên hệ.");
                return;
            }
            if (type.equals("SERVICE_FEEDBACK") && f.issueType == null) {
                fail(response, res, 400, "Góp ý thái độ phục vụ cần chọn vấn đề cần giải quyết (issueType).");
                return;
            }
            if ((type.equals("LOST_LUGGAGE") || type.equals("OTHER")) && f.content == null) {
                fail(response, res, 400, "Cần nhập nội dung mô tả (content).");
                return;
            }

            int recentCount = dao.countRecentComplaints(f.phone, f.email, RATE_LIMIT_WINDOW_MINUTES);
            if (recentCount >= RATE_LIMIT_MAX_REQUESTS) {
                fail(response, res, 429, "Bạn đã gửi quá nhiều phản ánh trong thời gian ngắn. Vui lòng thử lại sau ít phút.");
                return;
            }

            int complaintId = dao.createComplaint(f);
            res.put("success", complaintId > 0);
            res.put("complaintId", complaintId);
            res.put("message", "Đã gửi phản ánh thành công. Chúng tôi sẽ liên hệ sớm.");
        } catch (Exception e) {
            response.setStatus(500);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        response.getWriter().print(gson.toJson(res));
    }

    private void fail(HttpServletResponse response, Map<String, Object> res, int status, String message)
            throws IOException {
        response.setStatus(status);
        res.put("success", false);
        res.put("message", message);
        response.getWriter().print(gson.toJson(res));
    }

    private String normalizeRegion(String region) {
        if (region == null) {
            return null;
        }
        return region.toUpperCase();
    }

    private JsonObject readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        if (sb.length() == 0) {
            return new JsonObject();
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private String getStr(JsonObject body, String key) {
        if (body.has(key) && !body.get(key).isJsonNull()) {
            String v = body.get(key).getAsString().trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }
}