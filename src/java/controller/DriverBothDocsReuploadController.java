package controller;

import com.google.gson.Gson;
import dao.AccountDAO;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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

// 🚀 ĐÃ SỬA TÊN URL ĐƯỜNG DẪN THEO ĐÚNG YÊU CẦU CỦA LEADER:
@WebServlet("/api/v1/driver/documents/reupload")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class DriverBothDocsReuploadController extends HttpServlet {

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
            String accountIdParam = request.getParameter("accountID");

            if (accountIdParam == null || accountIdParam.trim().isEmpty()) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu thông tin mã tài khoản tài xế (accountID).");
                out.print(gson.toJson(apiResponse));
                return;
            }

            int accountId = Integer.parseInt(accountIdParam.trim());

            // Đọc đồng thời 2 file từ form-data gửi lên
            Part cccdPart = request.getPart("identityCard");
            Part licensePart = request.getPart("driverLicense");

            if (cccdPart == null || cccdPart.getSize() == 0 || licensePart == null || licensePart.getSize() == 0) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Bắt buộc phải đính kèm đầy đủ cả 2 file ảnh mới (keys: identityCard và driverLicense).");
                out.print(gson.toJson(apiResponse));
                return;
            }

            String applicationPath = request.getServletContext().getRealPath("");
            String uploadFilePath = applicationPath + File.separator + UPLOAD_DIR;
            File uploadFolder = new File(uploadFilePath);
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            // Ghi file ảnh CCCD mới
            String cccdName = "reupload_cccd_" + UUID.randomUUID().toString() + "_" + getFileName(cccdPart);
            cccdPart.write(uploadFilePath + File.separator + cccdName);
            String cccdUrl = UPLOAD_DIR + "/" + cccdName;

            // Ghi file ảnh Bằng lái mới
            String licenseName = "reupload_license_" + UUID.randomUUID().toString() + "_" + getFileName(licensePart);
            licensePart.write(uploadFilePath + File.separator + licenseName);
            String licenseUrl = UPLOAD_DIR + "/" + licenseName;

            // Gọi hàm xử lý Transaction gom 2 lệnh đã viết trong DAO
            AccountDAO dao = new AccountDAO();
            boolean isSuccess = dao.reuploadBothDocuments(accountId, cccdUrl, licenseUrl);

            if (isSuccess) {
                apiResponse.put("success", true);
                apiResponse.put("message", "Đã tải lên lại toàn bộ hồ sơ thành công! Đang chờ Ban quản trị phê duyệt lại.");
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Cập nhật dữ liệu thất bại. Vui lòng kiểm tra lại Account ID xem có tồn tại giấy tờ cũ không.");
            }

        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Mã accountID phải nhập định dạng số nguyên.");
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
        return "image.png";
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