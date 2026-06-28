/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.ExtensionDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author asus
 */
@WebServlet("/api/v1/payments/momo/callback")
public class MomoCallbackController extends HttpServlet {

    private final ExtensionDAO dao = new ExtensionDAO();
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
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        
        try {
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            
            JsonObject momoRes = JsonParser.parseString(sb.toString()).getAsJsonObject();
            
            int resultCode = momoRes.get("resultCode").getAsInt();
            int paymentId = Integer.parseInt(momoRes.get("orderId").getAsString());
            
            String transId = momoRes.has("transId") ? momoRes.get("transId").getAsString() : "N/A";
            String payType = momoRes.has("payType") ? momoRes.get("payType").getAsString() : "MoMo";

            if (resultCode == 0) {
                dao.processSuccessfulPayment(paymentId, transId, payType);
            } 

            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"message\": \"Loi xu ly Webhook\"}");
        }
    }
}

    

    