package utils;

import dao.AccountDAO;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailUtils {

    private static final ExecutorService emailExecutor = Executors.newFixedThreadPool(3);

    // ⚠️ ĐIỀN THÔNG TIN THẬT CỦA BẠN VÀO ĐÂY ĐỂ CHẠY
    private static final String FROM_EMAIL = "nguyenduchuy23052006@gmail.com"; 
    private static final String APP_PASSWORD = "oufekhyjnvbcrzwi"; 

    /**
     * Gửi email luồng ngầm bất đồng bộ và tự động chèn nhật ký vào bảng EmailLog khi hoàn tất
     */
    public static void sendEmailAndLogAsync(Integer accountId, String toEmail, String subject, String content) {
        emailExecutor.submit(() -> {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(FROM_EMAIL, APP_PASSWORD);
                }
            });

            String emailStatus = "Failed"; 
            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(FROM_EMAIL, "FleetFlow System"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject, "UTF-8");
                message.setContent(content, "text/html; charset=UTF-8");

                Transport.send(message);
                emailStatus = "Success"; 
                System.out.println("LOG_SUCCESS: Đã gửi email thành công tới: " + toEmail);
            } catch (Exception e) {
                System.err.println("LOG_ERROR: Gửi email thất bại mạng SMTP.");
                e.printStackTrace();
            } finally {
                // TỰ ĐỘNG GHI LOG XUỐNG TABLE EMAILLOG Ở MỌI TRƯỜNG HỢP
                try {
                    AccountDAO dao = new AccountDAO();
                    dao.logEmail(null, accountId, subject, emailStatus);
                    System.out.println("LOG_DB: Đã ghi nhận trạng thái vào bảng EmailLog.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

public static String buildWelcomeTemplate(String fullName, String email, String roleName) {
    // Tự động dịch text hiển thị sang tiếng Việt cho thân thiện với User
    String displayRole = roleName.equals("Driver") ? "Tài xế đối tác (Driver)" : "Khách hàng thành viên (Customer)";
    
    return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 20px auto; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;\">"
         + "    <div style=\"background: linear-gradient(135deg, #1e3d59 0%, #17b978 100%); padding: 30px; text-align: center; color: white;\">"
         + "        <h1 style=\"margin: 0; font-size: 24px;\">FLEETFLOW VẬN TẢI CÔNG NGHỆ</h1>"
         + "    </div>"
         + "    <div style=\"padding: 30px; background-color: #ffffff; color: #334155; line-height: 1.6;\">"
         + "        <p style=\"font-size: 16px;\">Xin chào <strong>" + fullName + "</strong>,</p>"
         + "        <p>Tài khoản ứng dụng của bạn đã được khởi tạo và kích hoạt thành công trên hệ thống FleetFlow.</p>"
         + "        <div style=\"background-color: #f8fafc; border-left: 4px solid #17b978; padding: 15px; margin: 20px 0;\">"
         + "            <p style=\"margin: 5px 0;\"><strong>Tên đăng nhập (Email):</strong> " + email + "</p>"
         + "            <p style=\"margin: 5px 0;\"><strong>Quyền truy cập:</strong> <span style=\"color: #1e3d59; font-weight: bold;\">" + displayRole + "</span></p>"
         + "            <p style=\"margin: 5px 0;\"><strong>Trạng thái hệ thống:</strong> Active</p>"
         + "        </div>"
         + "        <p>Cảm ơn bạn đã lựa chọn tham gia và đồng hành cùng giải pháp vận tải FleetFlow!</p>"
         + "        <hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 25px 0;\">"
         + "        <small style=\"color: #64748b;\">Đây là email thông báo tự động từ hệ thống, vui lòng không phản hồi lại thư này.</small>"
         + "    </div>"
         + "</div>";
}

    public static String buildForgotPasswordTemplate(String temporaryPassword) {
        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 20px auto; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;\">"
             + "    <div style=\"background: #ff6f61; padding: 30px; text-align: center; color: white;\">"
             + "        <h1 style=\"margin: 0;\">MẬT KHẨU TẠM THỜI</h1>"
             + "    </div>"
             + "    <div style=\"padding: 30px; background-color: #ffffff; color: #334155; line-height: 1.6;\">"
             + "        <p>Xin chào,</p>"
             + "        <p>Hệ thống FleetFlow đã đặt lại mật khẩu truy cập của bạn theo yêu cầu:</p>"
             + "        <div style=\"background: #f5f5f5; padding: 15px; font-size: 22px; font-weight: bold; text-align: center; color: #ff6f61; letter-spacing: 2px; margin: 20px 0; border: 1px dashed #ff6f61;\">"
             +              temporaryPassword
             + "        </div>"
             + "        <p style=\"color: #ff0000;\"><b>* Lưu ý:</b> Vui lòng sử dụng mật khẩu này để đăng nhập lại hệ thống và tiến hành đổi mật khẩu mới ngay lập tức để bảo vệ tài khoản.</p>"
             + "    </div>"
             + "</div>";
    }
    public static String buildChangePasswordTemplate(String fullName, String email) {
    return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 20px auto; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;\">"
         + "    <div style=\"background: #343a40; padding: 30px; text-align: center; color: white;\">"
         + "        <h1 style=\"margin: 0; font-size: 24px;\">THÔNG BÁO BẢO MẬT HỆ THỐNG</h1>"
         + "    </div>"
         + "    <div style=\"padding: 30px; background-color: #ffffff; color: #334155; line-height: 1.6;\">"
         + "        <p>Xin chào <strong>" + fullName + "</strong>,</p>"
         + "        <p>Chúng tôi xin thông báo mật khẩu tài khoản FleetFlow của bạn vừa được thay đổi thành công vào lúc <strong>" + new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(new java.util.Date()) + "</strong>.</p>"
         + "        <div style=\"background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; color: #664d03;\">"
         + "            <p style=\"margin: 0; font-size: 14px;\"><b>⚠️ Nếu KHÔNG phải bạn thực hiện hành động này:</b> Tài khoản của bạn có thể đang bị xâm nhập trái phép. Vui lòng liên hệ ngay với đội ngũ hỗ trợ kỹ thuật FleetFlow để được khóa tài khoản khẩn cấp.</p>"
         + "        </div>"
         + "        <p>Nếu bạn là người thực hiện đổi mật khẩu, vui lòng bỏ qua email thông báo này.</p>"
         + "        <hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 25px 0;\">"
         + "        <small style=\"color: #64748b;\">Trân trọng,<br>Hệ thống FleetFlow Security Team</small>"
         + "    </div>"
         + "</div>";
}
}