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

@WebServlet("/api/v1/driver/*") // 🚀 Thâu tóm toàn bộ mọi endpoint bắt đầu bằng cấu trúc này
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class DriverController extends HttpServlet {

    private static final String UPLOAD_DIR = "uploads";
    private final Gson gson = new Gson();

    // =========================================================================
    // 🔍 1. PHƯƠNG THỨC GET: ĐỌC DỮ LIỆU PROFILE & DASHBOARD TÀI XẾ
    // =========================================================================
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setupResponseHeaders(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();

        // Bóc tách endpoint con
        String action = request.getPathInfo();
        AccountDAO dao = new AccountDAO();

        try {
            String accountIdParam = request.getParameter("accountID");
            if (accountIdParam == null || accountIdParam.trim().isEmpty()) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu tham số mã tài khoản (accountID) trên URL.");
                out.print(gson.toJson(apiResponse));
                return;
            }
            int accountId = Integer.parseInt(accountIdParam.trim());

            if (action != null && action.equals("/profile")) {
                // 🔗 LINK API: /api/v1/driver/profile?accountID=xx
                Map<String, Object> driverData = dao.getDriverProfile(accountId);
                if (driverData != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", driverData);
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy hồ sơ tài xế hoặc tài khoản không phải vai trò Driver.");
                }
            } 
            else if (action != null && action.equals("/dashboard")) {
                // 🚀 LINK API MỚI: /api/v1/driver/dashboard?accountID=xx
                handleDriverDashboard(accountId, dao, apiResponse);
            } 
            else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                apiResponse.put("success", false);
                apiResponse.put("message", "Đường dẫn GET không tồn tại hoặc không hợp lệ.");
            }
        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Mã accountID truyền lên URL phải là ký tự số.");
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    // =========================================================================
    // ⚡ 2. PHƯƠNG THỨC POST: ĐIỀU HƯỚNG CÁC HÀNH ĐỘNG GHI/CẬP NHẬT DỮ LIỆU
    // =========================================================================
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setupResponseHeaders(response);
        request.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();

        String action = request.getPathInfo();
        AccountDAO dao = new AccountDAO();

        try {
            String accountIdParam = request.getParameter("accountID");
            if (accountIdParam == null || accountIdParam.trim().isEmpty()) {
                apiResponse.put("success", false);
                apiResponse.put("message", "Thiếu tham số bắt buộc accountID.");
                out.print(gson.toJson(apiResponse));
                return;
            }
            int accountId = Integer.parseInt(accountIdParam.trim());

            switch (action) {
                case "/documents/submit":
                    // 🔗 LINK API: /api/v1/driver/documents/submit
                    handleDocumentSubmit(request, accountId, dao, apiResponse);
                    break;

                case "/documents/reupload":
                    // 🔗 LINK API: /api/v1/driver/documents/reupload
                    handleDocumentReupload(request, accountId, dao, apiResponse);
                    break;

                case "/profile/update":
                    // 🔗 LINK API: /api/v1/driver/profile/update
                    handleProfileUpdate(request, accountId, dao, apiResponse);
                    break;

                case "/terms/accept":
                    // 🔗 LINK API: /api/v1/driver/terms/accept
                    boolean isTermsOk = dao.acceptDriverTerms(accountId);
                    if (isTermsOk) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Tài xế đã xác nhận đồng ý điều khoản dịch vụ thành công!");
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Không tìm thấy tài xế tương ứng hoặc cập nhật thất bại.");
                    }
                    break;

                default:
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Hành động POST (Action) không hợp lệ.");
                    break;
            }

        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Tham số accountID phải là ký tự số nguyên.");
        } catch (Exception e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "System Error: " + e.toString());
        } finally {
            out.print(gson.toJson(apiResponse));
            out.flush();
        }
    }

    // =========================================================================
    // 📂 3. CÁC HÀM XỬ LÝ NGHIỆP VỤ CON (SUB-LOGIC HELPER METHODS)
    // =========================================================================

    private void handleDriverDashboard(int accountId, AccountDAO dao, Map<String, Object> apiResponse) throws Exception {
        Map<String, Object> dashboardData = dao.getDriverDashboardMetrics(accountId);
        if (dashboardData != null) {
            apiResponse.put("success", true);
            apiResponse.put("data", dashboardData);
            apiResponse.put("message", "Tải dữ liệu thống kê cá nhân tài xế thành công.");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Không thể lấy dữ liệu thống kê tài xế.");
        }
    }

    private void handleDocumentSubmit(HttpServletRequest request, int accountId, AccountDAO dao, Map<String, Object> apiResponse) throws Exception {
        Part cccdPart = request.getPart("identityCard");
        Part licensePart = request.getPart("driverLicense");

        if ((cccdPart == null || cccdPart.getSize() == 0) && (licensePart == null || licensePart.getSize() == 0)) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Vui lòng đính kèm ít nhất ảnh CCCD hoặc Bằng lái xe để nộp hồ sơ.");
            return;
        }

        if (cccdPart != null && cccdPart.getSize() > 0 && dao.isDocTypeExist(accountId, "NationalID")) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Bạn đã nộp ảnh CCCD (NationalID) trước đó rồi. Vui lòng đợi Admin phê duyệt, không được nộp đè.");
            return;
        }
        if (licensePart != null && licensePart.getSize() > 0 && dao.isDocTypeExist(accountId, "DriverLicense")) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Bạn đã nộp ảnh Bằng lái (DriverLicense) trước đó rồi. Vui lòng đợi Admin phê duyệt, không được nộp đè.");
            return;
        }

        String uploadPath = request.getServletContext().getRealPath("") + File.separator + UPLOAD_DIR;
        ensureFolderExists(uploadPath);

        String cccdUrl = saveFile(cccdPart, "submit_cccd_", uploadPath);
        String licenseUrl = saveFile(licensePart, "submit_license_", uploadPath);

        boolean isSuccess = dao.submitDriverDocuments(accountId, cccdUrl, licenseUrl);
        if (isSuccess) {
            apiResponse.put("success", true);
            boolean isComplete = dao.isDriverDocumentsComplete(accountId);
            apiResponse.put("isProfileComplete", isComplete);
            apiResponse.put("message", isComplete 
                ? "Bạn đã nộp đầy đủ bộ hồ sơ (CCCD + Bằng lái)! Vui lòng chờ Ban quản trị kiểm tra và phê duyệt để bắt đầu nhận chuyến." 
                : "Tải tài liệu lên thành công! Tuy nhiên hồ sơ của bạn vẫn còn thiếu giấy tờ bắt buộc. Vui lòng bổ sung đầy đủ để được xét duyệt nhận chuyến.");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Nộp hồ sơ thất bại. Vui lòng kiểm tra lại Account ID.");
        }
    }

    private void handleDocumentReupload(HttpServletRequest request, int accountId, AccountDAO dao, Map<String, Object> apiResponse) throws Exception {
        Part cccdPart = request.getPart("identityCard");
        Part licensePart = request.getPart("driverLicense");

        if (cccdPart == null || cccdPart.getSize() == 0 || licensePart == null || licensePart.getSize() == 0) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Bắt buộc phải đính kèm đầy đủ cả 2 file ảnh mới (keys: identityCard và driverLicense).");
            return;
        }

        String uploadPath = request.getServletContext().getRealPath("") + File.separator + UPLOAD_DIR;
        ensureFolderExists(uploadPath);

        String cccdUrl = saveFile(cccdPart, "reupload_cccd_", uploadPath);
        String licenseUrl = saveFile(licensePart, "reupload_license_", uploadPath);

        boolean isSuccess = dao.reuploadBothDocuments(accountId, cccdUrl, licenseUrl);
        if (isSuccess) {
            apiResponse.put("success", true);
            apiResponse.put("message", "Đã tải lên lại toàn bộ hồ sơ thành công! Đang chờ Ban quản trị phê duyệt lại.");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Cập nhật dữ liệu thất bại. Vui lòng kiểm tra lại Account ID xem có tồn tại giấy tờ cũ không.");
        }
    }

    private void handleProfileUpdate(HttpServletRequest request, int accountId, AccountDAO dao, Map<String, Object> apiResponse) throws Exception {
        String fullName = request.getParameter("fullName");
        String phoneNumber = request.getParameter("phoneNumber");
        String availabilityStatus = request.getParameter("availabilityStatus");

        if (fullName == null || fullName.trim().isEmpty() || 
            phoneNumber == null || phoneNumber.trim().isEmpty() || 
            availabilityStatus == null || availabilityStatus.trim().isEmpty()) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Vui lòng điền đầy đủ Full Name, Phone Number và Availability Status.");
            return;
        }

        boolean isSuccess = dao.updateDriverProfile(accountId, fullName.trim(), phoneNumber.trim(), availabilityStatus.trim());
        if (isSuccess) {
            apiResponse.put("success", true);
            apiResponse.put("message", "Cập nhật thông tin hồ sơ tài xế thành công!");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Không tìm thấy thông tin tài xế để cập nhật.");
        }
    }

    // =========================================================================
    // ⚙️ TIỆN ÍCH HỖ TRỢ (HELPER UTILS)
    // =========================================================================
    private void setupResponseHeaders(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    private void ensureFolderExists(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private String saveFile(Part part, String prefix, String uploadPath) throws IOException {
        if (part == null || part.getSize() == 0) return null;
        String contentDisp = part.getHeader("content-disposition");
        String originalName = "document.png";
        for (String token : contentDisp.split(";")) {
            if (token.trim().startsWith("filename")) {
                originalName = token.substring(token.indexOf("=") + 2, token.length() - 1);
                break;
            }
        }
        String uniqueName = prefix + UUID.randomUUID().toString() + "_" + originalName;
        part.write(uploadPath + File.separator + uniqueName);
        return UPLOAD_DIR + "/" + uniqueName;
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