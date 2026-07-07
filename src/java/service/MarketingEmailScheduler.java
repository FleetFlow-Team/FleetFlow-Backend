package service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Background job: tự động chạy campaign "khách hàng quay lại sau 30 ngày" mỗi
 * PERIOD_HOURS giờ — không cần cron ngoài gọi vào endpoint /marketing/email/trigger nữa
 * (endpoint đó vẫn giữ lại để trigger thủ công lúc test/demo).
 */
@WebListener
public class MarketingEmailScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(MarketingEmailScheduler.class.getName());

    private static final long INITIAL_DELAY_SECONDS = 30;
    private static final long PERIOD_HOURS = 24;

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::runCampaign, INITIAL_DELAY_SECONDS, PERIOD_HOURS * 3600L, TimeUnit.SECONDS);
        LOG.info("[MarketingEmailScheduler] Đã khởi động — chạy mỗi " + PERIOD_HOURS + "h.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void runCampaign() {
        try {
            MarketingEmailService service = new MarketingEmailService();
            int count = service.runComebackCampaign();
            LOG.info("[MarketingEmailScheduler] Hoàn tất — đã gửi " + count + " email.");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[MarketingEmailScheduler] Lỗi chạy campaign: " + e.getMessage(), e);
        }
    }
}
