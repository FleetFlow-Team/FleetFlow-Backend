package service;

import dao.BookingDAO;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import model.Booking;

/**
 * Background job: tự động confirm booking PENDING sau AUTO_CONFIRM_TIMEOUT_SECONDS.
 *
 * Chạy mỗi 15 giây, quét tất cả booking PENDING đã quá timeout
 * mà dispatcher chưa action → tự approve + auto-dispatch driver.
 *
 * Dispatcher vẫn có thể reject thủ công TRƯỚC khi timeout hết.
 *
 * Cấu hình:
 *   AUTO_CONFIRM_TIMEOUT_SECONDS = 60 (1 phút) — thay đổi tùy yêu cầu.
 */
@WebListener
public class AutoConfirmScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AutoConfirmScheduler.class.getName());

    // Dispatcher có X giây để reject trước khi hệ thống tự confirm
    private static final int AUTO_CONFIRM_TIMEOUT_SECONDS = 10;

    // Account ID đại diện cho hệ thống khi ghi AuditLog (tạo 1 account "SYSTEM" trong DB)
    private static final int SYSTEM_ACCOUNT_ID = 1;

    private static final String SYSTEM_IP = "system";

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::runAutoConfirm, 15, 15, TimeUnit.SECONDS);
        LOG.info("[AutoConfirmScheduler] Đã khởi động — timeout = " + AUTO_CONFIRM_TIMEOUT_SECONDS + "s");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void runAutoConfirm() {
        try {
            BookingDAO bookingDAO = new BookingDAO();
            BookingWorkflowService workflowService = new BookingWorkflowService();

            List<Booking> overdue = bookingDAO.findPendingOverTimeout(AUTO_CONFIRM_TIMEOUT_SECONDS);

            for (Booking booking : overdue) {
                int bookingId = (int) booking.getId();
                try {
                    LOG.info("[AutoConfirm] Tự confirm booking #" + bookingId
                            + " (quá " + AUTO_CONFIRM_TIMEOUT_SECONDS + "s chưa có action)");
                    workflowService.approveBooking(bookingId, SYSTEM_ACCOUNT_ID, SYSTEM_IP);
                } catch (Exception e) {
                    // Có thể booking đã được dispatcher xử lý trong lúc chờ — bỏ qua
                    LOG.log(Level.WARNING,
                            "[AutoConfirm] Bỏ qua booking #" + bookingId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[AutoConfirm] Lỗi scheduler: " + e.getMessage(), e);
        }
    }
}