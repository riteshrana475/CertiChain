package com.certichain.wallet;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareAccessRepository extends JpaRepository<ShareAccessEvent, UUID> {
    List<ShareAccessEvent> findTop50ByShareIdOrderByCreatedAtDesc(UUID shareId);
}
