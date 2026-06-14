package controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import dao.CustomerDAO;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import model.BookingSummary;
import model.Customer;
import utils.AuthUtils;

/**
 *
 *
 */
@WebServlet("/api/v1/customer/*")
public class CustomerController extends HttpServlet {

    private final CustomerDAO customerDAO = new CustomerDAO();

    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Timestamp.class,
                    (JsonSerializer<Timestamp>) (src, type, ctx) ->
                            src == null ? null
                                    : new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(src)))
            .create();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        prepare(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String email = requireCustomer(request, response, apiResponse);
            if (email == null) {
                out.print(gson.toJson(apiResponse));
                out.flush();
                return;
            }

            String path = request.getPathInfo();

            if ("/profile".equals(path)) {
                // ---------- BE-3 ----------
                Customer profile = customerDAO.getProfileByEmail(email);
                if (profile == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy hồ sơ khách hàng.");
                } else {
                    apiResponse.put("success", true);
                    apiResponse.put("data", profile);
                }

            } else if ("/bookings".equals(path)) {
                // ---------- BE-7 ----------
                int customerId = customerDAO.getCustomerIdByEmail(email);
                List<BookingSummary> history = customerDAO.findBookingHistoryByCustomerId(customerId);
                apiResponse.put("success", true);
                apiResponse.put("count", history.size());
                apiResponse.put("data", history);

            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                apiResponse.put("success", false);
                apiResponse.put("message", "Endpoint không tồn tại.");
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            apiResponse.put("success", false);
            apiResponse.put("message", "Lỗi hệ thống: " + e.getMessage());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        prepare(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String email = requireCustomer(request, response, apiResponse);
            if (email == null) {
                out.print(gson.toJson(apiResponse));
                out.flush();
                return;
            }

            String path = request.getPathInfo();

            if ("/profile/update".equals(path)) {
                // ---------- BE-4 ----------
                String fullName    = trimToNull(request.getParameter("fullName"));
                String phoneNumber = trimToNull(request.getParameter("phoneNumber"));
                String address     = trimToNull(request.getParameter("address"));

                if (fullName == null && phoneNumber == null && address == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không có thông tin nào để cập nhật.");
                } else {
                    boolean ok = customerDAO.updateProfileByEmail(email, fullName, phoneNumber, address);
                    if (!ok) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Không tìm thấy tài khoản để cập nhật.");
                    } else {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Cập nhật hồ sơ thành công.");
                        apiResponse.put("data", customerDAO.getProfileByEmail(email));
                    }
                }

            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                apiResponse.put("success", false);
                apiResponse.put("message", "Endpoint không tồn tại.");
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            apiResponse.put("success", false);
            apiResponse.put("message", "Lỗi hệ thống: " + e.getMessage());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // ===================== HELPERS =====================

    private void prepare(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    /**
     * Xác thực JWT và bắt buộc role = Customer.
     * Trả email nếu hợp lệ; nếu không, set status (401/403) + thông điệp vào apiResponse và trả null.
     */
    private String requireCustomer(HttpServletRequest request, HttpServletResponse response,
                                   Map<String, Object> apiResponse) {
        String email = AuthUtils.getEmailIfValid(request);
        if (email == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            apiResponse.put("success", false);
            apiResponse.put("message", "Chưa đăng nhập hoặc token không hợp lệ/đã hết hạn.");
            return null;
        }
        String role = AuthUtils.getRoleIfValid(request);
        if (role == null || !role.equalsIgnoreCase("Customer")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            apiResponse.put("success", false);
            apiResponse.put("message", "Chỉ tài khoản Customer được truy cập chức năng này.");
            return null;
        }
        return email;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
