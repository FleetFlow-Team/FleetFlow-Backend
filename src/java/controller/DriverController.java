package controller;

import com.google.gson.Gson;
import dao.DriverDAO; // 🚀 Sử dụng DriverDAO độc quyền cho phân hệ tài xế
import model.Driver;   // 🚀 Import Model Driver để đồng bộ dữ liệu hướng đối tượng
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

@WebServlet("/api/v1/driver/*") // 🚀 Thâu tóm toàn bộ mọi endpoint của phân hệ Driver
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 10,       // 10MB
    maxRequestSize = 1024 * 1024 * 50     // 50MB
)
public class DriverController extends HttpServlet {

    private static final String UPLOAD_DIR = "uploads";
    private final Gson gson = new Gson();

    // =========================================================================
    // 🔍 1. PHƯƠNG THỨC GET: PROFILE, DASHBOARD, TRẠNG THÁI DUYỆT, VÍ & THỐNG KÊ
    // =========================================================================
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setupResponseHeaders(response);
        PrintWriter out = response.getWriter();
        Map<String, Object> apiResponse = new HashMap<>();

        String action = request.getPathInfo();
        DriverDAO dao = new DriverDAO(); 

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
                Driver driverData = dao.getDriverProfile(accountId);
                if (driverData != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", driverData); 
                    
                    // 🔔 CẢNH BÁO CHỦ ĐỘNG: Gửi cảnh báo nhắc nhở nếu chưa ký xác nhận điều khoản
                    if (!driverData.isTermsAccepted()) {
                        apiResponse.put("warning", "TermsNotAccepted");
                        apiResponse.put("warningMessage", "Tài khoản của bạn chưa thực hiện ký xác nhận điều khoản dịch vụ. Vui lòng hoàn tất ký kết.");
                    }
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy hồ sơ tài xế hoặc tài khoản đã bị ẩn/xóa mềm.");
                }
            } 
            else if (action != null && action.equals("/dashboard")) {
                // 🔗 LINK API: /api/v1/driver/dashboard?accountID=xx
                handleDriverDashboard(accountId, dao, apiResponse);
            } 
            else if (action != null && action.equals("/approval-status")) {
                // 🔗 LINK API: /api/v1/driver/approval-status?accountID=xx
                Map<String, Object> approvalData = dao.getDriverApprovalDetail(accountId);
                if (approvalData != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", approvalData);
                    apiResponse.put("message", "Tải trạng thái phê duyệt hồ sơ và lý do từ chối thành công.");
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Tài khoản không tồn tại thông tin tài xế hoạt động.");
                }
            } 
            else if (action != null && action.equals("/wallet")) {
                // 🔗 LINK API: /api/v1/driver/wallet?accountID=xx
                Map<String, Object> walletData = dao.getDriverWallet(accountId);
                if (walletData != null) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", walletData);
                    apiResponse.put("message", "Tải thông tin số dư và trạng thái ví công nợ tài xế thành công.");
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy thông tin ví của tài xế này hoặc ví đã bị khóa.");
                }
            }
            else if (action != null && action.equals("/income-summary")) {
                // 🔗 LINK API MỚI: /api/v1/driver/income-summary?accountID=xx
                Map<String, Object> incomeData = dao.getDriverIncomeSummary(accountId);
                if (incomeData != null && !incomeData.isEmpty()) {
                    apiResponse.put("success", true);
                    apiResponse.put("data", incomeData);
                    apiResponse.put("message", "Tải bảng thống kê báo cáo thu nhập tài xế thành công.");
                } else {
                    apiResponse.put("success", false);
                    apiResponse.put("message", "Không tìm thấy dữ liệu báo cáo thu nhập của tài xế này.");
                }
            }
            else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                apiResponse.put("success", false);
                apiResponse.put("message", "Đường dẫn GET không tồn tại hoặc không hợp lệ.");
            }
        } catch (NumberFormatException e) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Mã accountID truyền lên URL phải là ký tự số nguyên.");
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
        DriverDAO dao = new DriverDAO(); 

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
                    handleDocumentSubmit(request, response, accountId, dao, apiResponse);
                    return; // Điều hướng luồng in dữ liệu riêng của hàm con

                case "/documents/reupload":
                    handleDocumentReupload(request, response, accountId, dao, apiResponse);
                    return; // Điều hướng luồng in dữ liệu riêng của hàm con

                case "/profile/update":
                    handleProfileUpdate(request, response, accountId, dao, apiResponse);
                    return; // Điều hướng luồng in dữ liệu riêng của hàm con

                case "/terms/accept": // 🔗 GIỮ NGUYÊN ĐƯỜNG DẪN GỐC CỦA BẠN KHÔNG ĐỔI
                    boolean isTermsOk = dao.acceptDriverTerms(accountId);
                    if (isTermsOk) {
                        apiResponse.put("success", true);
                        apiResponse.put("message", "Tài xế đã xác nhận đồng ý điều khoản dịch vụ thành công!");
                    } else {
                        apiResponse.put("success", false);
                        apiResponse.put("message", "Không tìm thấy tài xế tương ứng hoặc tài khoản không hợp lệ.");
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
            // Chỉ chạy khối in mặc định nếu không thuộc 3 hàm đã tự xử lý PrintWriter ở dưới
            if (!"/profile/update".equals(action) && !"/documents/submit".equals(action) && !"/documents/reupload".equals(action)) {
                out.print(gson.toJson(apiResponse));
                out.flush();
            }
        }
    }

    // =========================================================================
    // 📂 3. CÁC HÀM XỬ LÝ NGHIỆP VỤ CON (SUB-LOGIC HELPER METHODS)
    // =========================================================================

    private void handleDriverDashboard(int accountId, DriverDAO dao, Map<String, Object> apiResponse) throws Exception {
        Map<String, Object> dashboardData = dao.getDriverDashboardMetrics(accountId);
        if (dashboardData != null) {
            apiResponse.put("success", true);
            apiResponse.put("data", dashboardData);
            apiResponse.put("message", "Tải dữ liệu thống kê cá nhân tài xế từ bảng DriverEarning thành công.");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Không thể lấy dữ liệu thống kê tài xế (Mã tài khoản không tồn tại hoặc bị ẩn).");
        }
    }

    private void handleDocumentSubmit(HttpServletRequest request, HttpServletResponse response, int accountId, DriverDAO dao, Map<String, Object> apiResponse) throws Exception {
        PrintWriter out = response.getWriter();
        
        // 🛑 PHÒNG THỦ KHÓA CỔNG CHẶN NỘP ĐƠN MỚI NẾU CHƯA KÝ XÁC NHẬN ĐIỀU KHOẢN
        Driver driver = dao.getDriverProfile(accountId);
        if (driver != null && !driver.isTermsAccepted()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            apiResponse.put("success", false);
            apiResponse.put("message", "Thao tác bị từ chối! Bạn phải bấm xác nhận đồng ý với điều khoản dịch vụ trước khi tiến hành nộp hồ sơ giấy tờ.");
            out.print(gson.toJson(apiResponse));
            return;
        }

        Part cccdPart = request.getPart("identityCard");
        Part licensePart = request.getPart("driverLicense");

        if (cccdPart == null || cccdPart.getSize() == 0 || licensePart == null || licensePart.getSize() == 0) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Hồ sơ không hợp lệ! Bắt buộc phải nộp đồng thời cả 2 loại giấy tờ: ảnh CCCD (identityCard) và ảnh Bằng lái xe (driverLicense).");
            out.print(gson.toJson(apiResponse));
            return;
        }

        if (dao.isDocTypeExist(accountId, "NationalID") || dao.isDocTypeExist(accountId, "DriverLicense")) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Tài khoản này đã có lịch sử nộp hồ sơ trên hệ thống. Vui lòng đợi Ban quản trị duyệt.");
            out.print(gson.toJson(apiResponse));
            return;
        }

        String uploadPath = request.getServletContext().getRealPath("") + File.separator + UPLOAD_DIR;
        ensureFolderExists(uploadPath);

        String cccdUrl = saveFile(cccdPart, "submit_cccd_", uploadPath);
        String licenseUrl = saveFile(licensePart, "submit_license_", uploadPath);

        boolean isSuccess = dao.submitDriverDocuments(accountId, cccdUrl, licenseUrl);
        if (isSuccess) {
            apiResponse.put("success", true);
            apiResponse.put("isProfileComplete", true); 
            apiResponse.put("message", "Bạn đã nộp đầy đủ bộ hồ sơ (CCCD + Bằng lái) thành công! Vui lòng chờ Ban quản trị hệ thống kiểm tra và phê duyệt.");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Nộp hồ sơ thất bại. Nguyên nhân: Mã accountID không tồn tại trong hệ thống bảng Account.");
        }
        out.print(gson.toJson(apiResponse));
    }

    private void handleDocumentReupload(HttpServletRequest request, HttpServletResponse response, int accountId, DriverDAO dao, Map<String, Object> apiResponse) throws Exception {
        PrintWriter out = response.getWriter();
        
        // 🛑 PHÒNG THỦ KHÓA CỔNG CHẶN REUPLOAD LẠI HỒ SƠ NẾU CHƯA KÝ XÁC NHẬN ĐIỀU KHOẢN
        Driver driver = dao.getDriverProfile(accountId);
        if (driver != null && !driver.isTermsAccepted()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            apiResponse.put("success", false);
            apiResponse.put("message", "Thao tác bị từ chối! Bạn phải bấm xác nhận đồng ý với điều khoản dịch vụ trước khi nộp lại hồ sơ lỗi.");
            out.print(gson.toJson(apiResponse));
            return;
        }

        Part cccdPart = request.getPart("identityCard");
        Part licensePart = request.getPart("driverLicense");

        if (cccdPart == null || cccdPart.getSize() == 0 || licensePart == null || licensePart.getSize() == 0) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Bắt buộc phải đính kèm đầy đủ cả 2 file ảnh mới (keys: identityCard và driverLicense).");
            out.print(gson.toJson(apiResponse));
            return;
        }

        String uploadPath = request.getServletContext().getRealPath("") + File.separator + UPLOAD_DIR;
        ensureFolderExists(uploadPath);

        String cccdUrl = saveFile(cccdPart, "reupload_cccd_", uploadPath);
        String licenseUrl = saveFile(licensePart, "reupload_license_", uploadPath);

        boolean isSuccess = dao.reuploadBothDocuments(accountId, cccdUrl, licenseUrl);
        if (isSuccess) {
            apiResponse.put("success", true);
            apiResponse.put("message", "Đã tải lên lại toàn bộ hồ sơ thành công! Hệ thống đã reset trường lý do lỗi, đang chờ Ban quản trị duyệt lại.");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Cập nhật dữ liệu thất bại. Vui lòng kiểm tra lại Account ID xem có tồn tại giấy tờ cũ không.");
        }
        out.print(gson.toJson(apiResponse));
    }

    private void handleProfileUpdate(HttpServletRequest request, HttpServletResponse response, int accountId, DriverDAO dao, Map<String, Object> apiResponse) throws Exception {
        PrintWriter out = response.getWriter();
        String fullName = request.getParameter("fullName");
        String phoneNumber = request.getParameter("phoneNumber");
        String availabilityStatus = request.getParameter("availabilityStatus");

        if (fullName == null || fullName.trim().isEmpty() || 
            phoneNumber == null || phoneNumber.trim().isEmpty() || 
            availabilityStatus == null || availabilityStatus.trim().isEmpty()) {
            apiResponse.put("success", false);
            apiResponse.put("message", "Vui lòng điền đầy đủ Full Name, Phone Number và Availability Status.");
            out.print(gson.toJson(apiResponse));
            return;
        }

        // 🛑 PHÒNG THỦ TOÀN DIỆN: Kiểm tra cờ điều khoản, nếu chưa ký (false) -> CHẶN ĐỨNG HOÀN TOÀN TẤT CẢ trạng thái gửi lên
        Driver driver = dao.getDriverProfile(accountId);
        if (driver != null && !driver.isTermsAccepted()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // Trả lỗi 400 Bad Request
            apiResponse.put("success", false);
            apiResponse.put("message", "Thao tác bị từ chối! Bạn phải bấm xác nhận đồng ý với điều khoản dịch vụ trước khi cập nhật hồ sơ hoặc trạng thái.");
            out.print(gson.toJson(apiResponse));
            return; // Ngắt luồng ngay, không cho cập nhật xuống database
        }

        // Nếu hợp lệ (đã ký điều khoản) -> Tiến hành cập nhật bình thường như cũ
        boolean isSuccess = dao.updateDriverProfile(accountId, fullName.trim(), phoneNumber.trim(), availabilityStatus.trim());
        if (isSuccess) {
            apiResponse.put("success", true);
            apiResponse.put("message", "Cập nhật thông tin hồ sơ và trạng thái trực tuyến tài xế thành công!");
        } else {
            apiResponse.put("success", false);
            apiResponse.put("message", "Không tìm thấy thông tin tài xế hợp lệ để cập nhật.");
        }
        out.print(gson.toJson(apiResponse));
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