package com.certichain.wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHash(String tokenHash);

    List<ShareLink> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerIdAndRevokedAtIsNullAndExpiresAtAfter(UUID ownerId, java.time.Instant now);

    List<ShareLink> findByRevokedAtIsNullAndExpiresAtBetween(java.time.Instant start, java.time.Instant end);
}
