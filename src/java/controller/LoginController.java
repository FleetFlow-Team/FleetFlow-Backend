/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

/**
 *
 * @author nguye
 */
import dao.AccountDAO;
import model.Account;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
/**
 *
 * @author User
 */
@WebServlet(name = "LoginController", urlPatterns = {"/LoginController"})
public class LoginController extends HttpServlet {

    private static final String LOGIN_PAGE = "/login.jsp";
    private static final String ADMIN_PAGE = "/admin.jsp";
    private static final String CUSTOMER_PAGE = "/index.jsp";
    private static final String DRIVER_PAGE = "/driver.jsp";
    
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final String ROLE_DRIVER = "DRIVER";

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        String url = LOGIN_PAGE;
        try {
            // Đọc trực tiếp tham số từ form gửi lên
            String email = request.getParameter("email");
            String password = request.getParameter("password");
            
            AccountDAO dao = new AccountDAO();
            Account loginUser = dao.checkLogin(email, password);
            
            if (loginUser != null) {
                if ("LOCKED".equalsIgnoreCase(loginUser.getStatus())) {
                    request.setAttribute("ERROR_MESSAGE", "Login failed: Your account is currently LOCKED!");
                } else {
                    HttpSession session = request.getSession();
                    session.setAttribute("LOGIN_USER", loginUser);
                    
                    String roleName = loginUser.getRoleName();
                    if (ROLE_ADMIN.equalsIgnoreCase(roleName)) {
                        url = ADMIN_PAGE;
                    } else if (ROLE_CUSTOMER.equalsIgnoreCase(roleName)) {
                        url = CUSTOMER_PAGE;
                    } else if (ROLE_DRIVER.equalsIgnoreCase(roleName)) {
                        url = DRIVER_PAGE;
                    } else {
                        request.setAttribute("ERROR_MESSAGE", "Access denied: The role [" + roleName + "] is not supported.");
                    }
                }
            } else {
                request.setAttribute("ERROR_MESSAGE", "Login failed: Incorrect email or password.");
            }
            
        } catch (Exception e) {
            log("Error at LoginController: " + e.toString());
        } finally {
            // Tự quyết định forward về trang đích luôn, không trả quyền về MainController nữa
            request.getRequestDispatcher(url).forward(request, response);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Login Controller with javax and API Path";
    }// </editor-fold>

}
