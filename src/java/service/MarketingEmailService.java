package service;

import dao.ExtensionDAO;
import dao.RatingDAO;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import utils.EmailUtils;

/**
 * Campaign "khách hàng quay lại sau 30 ngày": quét khách không đặt chuyến nào
 * trong 30 ngày gần nhất, tặng voucher và gửi email thật (không log console).
 * Dùng chung bởi MarketingEmailScheduler (tự động) và MarketingEmailController
 * (trigger thủ công qua API).
 */
public class MarketingEmailService {

    private static final Logger LOG = Logger.getLogger(MarketingEmailService.class.getName());

    private static final int INACTIVE_DAYS = 30;
    // Account đại diện hệ thống khi tạo campaign/voucher tự động — cùng convention với AutoConfirmScheduler
    private static final int SYSTEM_ACCOUNT_ID = 1;

    private final RatingDAO ratingDAO = new RatingDAO();
    private final ExtensionDAO extensionDAO = new ExtensionDAO();

    public int runComebackCampaign() throws Exception {
        int campaignId = extensionDAO.getOrCreateComebackCampaign(SYSTEM_ACCOUNT_ID);
        String voucherCode = extensionDAO.getOrCreateComebackVoucherCode(campaignId, SYSTEM_ACCOUNT_ID);

        List<Map<String, Object>> targets = ratingDAO.getInactiveCustomers(INACTIVE_DAYS, campaignId);

        String subject = "FleetFlow nhớ bạn! Ưu đãi cho chuyến đi tiếp theo";
        String discountText = "Giảm 10% (tối đa 50.000đ)";
        String expiryText = "trong vòng 30 ngày kể từ hôm nay";

        for (Map<String, Object> target : targets) {
            int accountId = (int) target.get("AccountID");
            String email = (String) target.get("Email");
            String fullName = (String) target.get("FullName");

            String content = EmailUtils.buildComebackVoucherTemplate(fullName, voucherCode, discountText, expiryText);
            EmailUtils.sendEmailAndLogAsync(campaignId, accountId, email, subject, content);
        }

        extensionDAO.touchCampaignLastRun(campaignId);
        LOG.info("[MarketingEmailService] Comeback campaign: gửi tới " + targets.size()
                + " khách hàng inactive >= " + INACTIVE_DAYS + " ngày (voucher " + voucherCode + ").");
        return targets.size();
    }
}
