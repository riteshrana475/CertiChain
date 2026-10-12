package com.certichain.wallet;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShareReminder {

    private final ShareLinkRepository links;
    private final NotificationRepository notifications;
    private final ActivityRecorder activity;

    public ShareReminder(ShareLinkRepository links, NotificationRepository notifications, ActivityRecorder activity) {
        this.links = links;
        this.notifications = notifications;
        this.activity = activity;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 20_000)
    public void remind() {
        Instant now = Instant.now();
        for (ShareLink link : links.findByRevokedAtIsNullAndExpiresAtBetween(now, now.plus(24, ChronoUnit.HOURS))) {
            String href = "/wallet/shared?focus=" + link.getId();
            if (notifications.existsByRecipientIdAndEventTypeAndHref(link.getOwnerId(), "SHARE_EXPIRING", href)) {
                continue;
            }
            activity.notify(
                    link.getOwnerId(),
                    "SHARE_EXPIRING",
                    "Share link expiring soon",
                    "A share link expires within 24 hours. Revoking it stops future visits. It does not delete a copy someone already downloaded.",
                    href);
        }
    }
}
