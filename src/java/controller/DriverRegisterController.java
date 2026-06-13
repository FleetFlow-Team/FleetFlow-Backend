package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import model.Account;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import utils.EmailUtils;
import utils.PasswordUtils;

@WebServlet("/api/v1/driver/register")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class DriverRegisterController extends HttpServlet {

    private static final String UPLOAD_DIR = "uploads";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8"); 
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();
        Map<String, Object> apiResponse = new HashMap<>();

        try {
            String email = request.getParameter("email");
            String password = request.getParameter("password");
            String fullName = request.getParameter("fullName");
            String phoneNumber = request.getParameter("phoneNumber");

            if (email != null) email = email.trim();
            if (password != null) password = password.trim();
            if (fullName != null) fullName = fullName.trim();
            if (phoneNumber != null) phoneNumber = phoneNumber.trim();

            if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {
                
                if (fullName == null || fullName.isEmpty()) {
                    fullName = email.split("@")[0];
                }

                AccountDAO dao = new AccountDAO();
                boolean isExist = dao.checkEmailExist(email);

                if (!isExist) {
                    String identityCardURL = null;
                    String driverLicenseURL = null;

                    String applicationPath = request.getServletContext().getRealPath("");
                    String uploadFilePath = applicationPath + File.separator + UPLOAD_DIR;
                    File uploadFolder = new File(uploadFilePath);
                    if (!uploadFolder.exists()) {
                        uploadFolder.mkdirs();
                    }

                    // 1. Lưu file ảnh CCCD
                    Part cccdPart = request.getPart("identityCard");
                    if (cccdPart != null && cccdPart.getSize() > 0) {
                        String fileName = "cccd_" + UUID.randomUUID().toString() + "_" + getFileName(cccdPart);
                        cccdPart.write(uploadFilePath + File.separator + fileName);
                        identityCardURL = UPLOAD_DIR + "/" + fileName;
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Tài xế bắt buộc phải tải lên ảnh CCCD.");
                        out.print(gson.toJson(apiResponse));
                        return;
                    }

                    // 2. Lưu file ảnh Bằng lái xe
                    Part licensePart = request.getPart("driverLicense");
                    if (licensePart != null && licensePart.getSize() > 0) {
                        String fileName = "license_" + UUID.randomUUID().toString() + "_" + getFileName(licensePart);
                        licensePart.write(uploadFilePath + File.separator + fileName);
                        driverLicenseURL = UPLOAD_DIR + "/" + fileName;
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Tài xế bắt buộc phải tải lên Bằng lái xe.");
                        out.print(gson.toJson(apiResponse));
                        return;
                    }

                    // Mã hóa mật khẩu bằng BCrypt cho tài xế mới
                    String dbHashedPassword = PasswordUtils.hashPassword(password);
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    Account newAcc = new Account("Driver", email, dbHashedPassword, fullName, phoneNumber, "Active", now, now);
                    
                    // Thực thi hàm Transaction lưu đồng thời Account và bảng IdentityDocument phụ trợ
                    boolean isCreated = dao.registerAccountWithDocs(newAcc, identityCardURL, driverLicenseURL);
                    
                    if (isCreated) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Đăng ký tài khoản tài xế và nộp hồ sơ thành công!");

                        int newAccountId = dao.getAccountIdByEmail(email);
                        String mailSubject = "[FleetFlow] Hồ Sơ Đăng Ký Tài Xế Đang Được Xét Duyệt";
                        String mailContent = EmailUtils.buildWelcomeTemplate(fullName, email, "Driver");
                        EmailUtils.sendEmailAndLogAsync(newAccountId, email, mailSubject, mailContent);
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Hệ thống DB từ chối lưu dữ liệu tài xế.");
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Email này đã được sử dụng.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng cung cấp Email và Mật khẩu đăng ký.");
            }
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    private String getFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        String[] tokens = contentDisp.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf("=") + 2, token.length() - 1);
            }
        }
        return "unknown.png";
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}