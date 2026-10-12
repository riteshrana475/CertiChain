package com.certichain.wallet;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findTop30ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByRecipientIdAndEventTypeAndHref(UUID recipientId, String eventType, String href);
}
