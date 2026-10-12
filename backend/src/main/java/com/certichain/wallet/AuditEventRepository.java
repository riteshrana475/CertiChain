package com.certichain.wallet;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findTop20ByActorEmailOrderByCreatedAtDesc(String actorEmail);
}
