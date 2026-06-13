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

@WebServlet("/api/v1/driver/documents/submit")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class DriverDocumentSubmitController extends HttpServlet {

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
                apiResponse.put("message", "Thiếu mã tài khoản tài xế nộp hồ sơ (accountID).");
                out.print(gson.toJson(apiResponse));
                return;
            }

            int accountId = Integer.parseInt(accountIdParam.trim());

            // Bốc tách tệp tin ảnh từ form-data (Có thể một trong hai trường bị bỏ trống)
            Part cccdPart = request.getPart("identityCard");
            Part licensePart = request.getPart("driverLicense");

            // 🚨 SỬA ĐỔI 1: Kiểm tra nếu cả 2 trường đều không truyền gì lên thì mới báo lỗi
            if ((cccdPart == null || cccdPart.getSize() == 0) && (licensePart == null || licensePart.getSize() == 0)) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Vui lòng đính kèm ít nhất ảnh CCCD hoặc Bằng lái xe để nộp hồ sơ.");
                out.print(gson.toJson(apiResponse));
                return;
            }

            // Thiết lập vị trí thư mục lưu tệp cục bộ trên Server
            String applicationPath = request.getServletContext().getRealPath("");
            String uploadFilePath = applicationPath + File.separator + UPLOAD_DIR;
            File uploadFolder = new File(uploadFilePath);
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            String cccdUrl = null;
            String licenseUrl = null;

            // Xử lý ghi file ảnh CCCD nếu có
            if (cccdPart != null && cccdPart.getSize() > 0) {
                String cccdName = "submit_cccd_" + UUID.randomUUID().toString() + "_" + getFileName(cccdPart);
                cccdPart.write(uploadFilePath + File.separator + cccdName);
                cccdUrl = UPLOAD_DIR + "/" + cccdName;
            }

            // Xử lý ghi file ảnh Bằng lái nếu có
            if (licensePart != null && licensePart.getSize() > 0) {
                String licenseName = "submit_license_" + UUID.randomUUID().toString() + "_" + getFileName(licensePart);
                licensePart.write(uploadFilePath + File.separator + licenseName);
                licenseUrl = UPLOAD_DIR + "/" + licenseName;
            }

            // Thực hiện gọi hàm lưu trữ linh hoạt dưới DAO
            AccountDAO dao = new AccountDAO();
            boolean isSuccess = dao.submitDriverDocuments(accountId, cccdUrl, licenseUrl);

            if (isSuccess) {
                apiResponse.put("success", true);
                
                // 🚀 SỬA ĐỔI 2: Tự động kiểm tra xem tổng số giấy tờ dưới DB đã đủ 2 loại chưa
                boolean isComplete = dao.isDriverDocumentsComplete(accountId);
                
                if (isComplete) {
                    apiResponse.put("isProfileComplete", true);
                    apiResponse.put("message", "Bạn đã nộp đầy đủ bộ hồ sơ (CCCD + Bằng lái)! Vui lòng chờ Ban quản trị kiểm tra và phê duyệt để bắt đầu nhận chuyến.");
                } else {
                    apiResponse.put("isProfileComplete", false);
                    apiResponse.put("message", "Tải tài liệu lên thành công! Tuy nhiên hồ sơ của bạn vẫn còn thiếu giấy tờ bắt buộc. Vui lòng bổ sung đầy đủ để được xét duyệt nhận chuyến.");
                }
            } else {
                apiResponse.put("success", false);
                apiResponse.put("message", "Nộp hồ sơ thất bại. Vui lòng kiểm tra lại Account ID.");
            }

        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Mã accountID phải là ký tự số nguyên.");
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
        return "document.png";
    }
}