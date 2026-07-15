/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.RatingDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/ratings/customer")
public class CustomerRatingController extends HttpServlet {

    private final RatingDAO dao = new RatingDAO();
    private final Gson gson = new Gson();

    private static final int MAX_RATINGS_PER_BOOKING = 2;
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> res = new HashMap<>();

        try {
            BufferedReader reader = request.getReader();
            JsonObject body = JsonParser.parseReader(reader).getAsJsonObject();

            int bookingId = body.get("bookingId").getAsInt();
            int driverRating = body.get("driverRating").getAsInt();
            int carRating = body.get("carRating").getAsInt();
            String comment = body.has("comment") ? body.get("comment").getAsString() : "";

            if (dao.isCustomerRatingLocked(bookingId)) {
                res.put("success", false);
                res.put("message", "Đã quá 7 ngày kể từ khi kết thúc chuyến đi. Bạn không thể đánh giá nữa.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(gson.toJson(res));
                return;
            }

            if (dao.countCustomerRating(bookingId) >= MAX_RATINGS_PER_BOOKING) {
                res.put("success", false);
                res.put("message", "Bạn đã đánh giá chuyến đi này đủ số lần cho phép (tối đa " + MAX_RATINGS_PER_BOOKING + " lần).");
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print(gson.toJson(res));
                return;
            }

            boolean isSuccess = dao.submitCustomerRating(bookingId, driverRating, carRating, comment);
            if (isSuccess) {
                res.put("success", true);
                res.put("message", "Cảm ơn bạn đã đánh giá chuyến đi!");
            } else {
                res.put("success", false);
                res.put("message", "Không thể lưu đánh giá. Vui lòng thử lại.");
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.put("success", false);
            res.put("message", "Lỗi server: " + e.getMessage());
        }
        out.print(gson.toJson(res));
    }
}