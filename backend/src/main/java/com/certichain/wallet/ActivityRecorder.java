package com.certichain.wallet;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ActivityRecorder {

    private final NotificationRepository notifications;
    private final AuditEventRepository audits;

    public ActivityRecorder(NotificationRepository notifications, AuditEventRepository audits) {
        this.notifications = notifications;
        this.audits = audits;
    }

    public void notify(UUID recipientId, String type, String title, String message, String href) {
        AppNotification notice = new AppNotification();
        notice.setId(UUID.randomUUID());
        notice.setRecipientId(recipientId);
        notice.setEventType(type);
        notice.setTitle(title);
        notice.setMessage(message);
        notice.setHref(href == null ? "" : href);
        notice.setCreatedAt(Instant.now());
        notifications.save(notice);
    }

    public void audit(String actorEmail, String type, String targetType, String targetId, String detail) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setActorEmail(actorEmail == null ? "" : actorEmail);
        event.setEventType(type);
        event.setTargetType(targetType);
        event.setTargetId(targetId == null ? "" : targetId);
        event.setDetail(detail == null ? "" : detail);
        event.setCreatedAt(Instant.now());
        audits.save(event);
    }
}
