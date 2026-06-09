package controller;



import com.google.gson.Gson;

import dao.AccountDAO;

import model.Account;

import java.io.IOException;

import java.io.PrintWriter;

import java.util.HashMap;

import java.util.Map;

import javax.servlet.ServletException;

import javax.servlet.annotation.WebServlet;

import javax.servlet.http.HttpServlet;

import javax.servlet.http.HttpServletRequest;

import javax.servlet.http.HttpServletResponse;

import javax.servlet.http.HttpSession;



@WebServlet("/api/v1/auth/login")

public class LoginController extends HttpServlet {



    // API thì KHÔNG cần hằng số dẫn đến các trang JSP nữa ngoài trừ xử lý logic role

    private static final String ROLE_ADMIN = "Admin";

    private static final String ROLE_CUSTOMER = "Customer";



    @Override

    protected void doGet(HttpServletRequest request, HttpServletResponse response)

            throws ServletException, IOException {

        response.setContentType("application/json");

        response.setCharacterEncoding("UTF-8");

        response.setHeader("Access-Control-Allow-Origin", "*");

        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED); // Code 405



        Map<String, Object> apiResponse = new HashMap<>();

        apiResponse.put("success", false);

        apiResponse.put("message", "HTTP method GET is not supported by this URL. Please use POST for login.");



        Gson gson = new Gson();

        response.getWriter().print(gson.toJson(apiResponse));

    }



    @Override

    protected void doPost(HttpServletRequest request, HttpServletResponse response)

            throws ServletException, IOException {



        // 1. Cấu hình Response trả về kiểu JSON và hỗ trợ tiếng Việt

        response.setContentType("application/json");

        response.setCharacterEncoding("UTF-8");



        // Cấu hình CORS để Frontend ở port khác (ví dụ VS Code Live Server) có thể gọi tới

        response.setHeader("Access-Control-Allow-Origin", "*");

        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");

        response.setHeader("Access-Control-Allow-Headers", "Content-Type");



        PrintWriter out = response.getWriter();

        Gson gson = new Gson();



        // Tạo một Map để chứa kết quả trả về cho Frontend

        Map<String, Object> apiResponse = new HashMap<>();



        try {

            // Lấy email và password từ request (Frontend gửi lên dạng form-urlencoded)

            String email = request.getParameter("email");

            String password = request.getParameter("password");



            if (email != null && password != null) {

                AccountDAO dao = new AccountDAO();

                Account loginUser = dao.checkLogin(email, password);



                if (loginUser != null) {

                    // Đăng nhập ĐÚNG: Tạo session lưu trữ như cũ (nếu cần)

                    HttpSession session = request.getSession();

                    session.setAttribute("LOGIN_USER", loginUser);



                    // Phản hồi thành công cho Frontend

                    apiResponse.put("success", true);

                    apiResponse.put("message", "Login successful");



                    // Trả thêm thông tin user và role để Frontend biết đường tự chuyển trang

                    Map<String, Object> userData = new HashMap<>();

                    userData.put("email", loginUser.getEmail());

                    userData.put("fullName", loginUser.getFullName());

                    userData.put("roleName", loginUser.getRoleName());

                    apiResponse.put("user", userData);



                } else {

                    // Đăng nhập SAI

                    apiResponse.put("success", false);

                    apiResponse.put("message", "Incorrect email or password");

                }

            } else {

                apiResponse.put("success", false);

                apiResponse.put("message", "Missing email or password");

            }

        } catch (Exception e) {

            log("Error at LoginController API: " + e.toString());

            apiResponse.put("success", false);

            apiResponse.put("message", "Internal Server Error: " + e.getMessage());

        } finally {

            // 2. Biến Map thành chuỗi JSON và in ra response

            String jsonResult = gson.toJson(apiResponse);

            out.print(jsonResult);

            out.flush();

        }

    }



    // Hỗ trợ method OPTIONS cho CORS (Khi frontend gọi API bằng Fetch/Axios hay kích hoạt cái này)

    @Override

    protected void doOptions(HttpServletRequest request, HttpServletResponse response)

            throws ServletException, IOException {

        response.setHeader("Access-Control-Allow-Origin", "*");

        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");

        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        response.setStatus(HttpServletResponse.SC_OK);

    }

} 

