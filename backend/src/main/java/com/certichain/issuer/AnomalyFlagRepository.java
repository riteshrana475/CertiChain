package com.certichain.issuer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyFlagRepository extends JpaRepository<AnomalyFlag, UUID> {
    List<AnomalyFlag> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);
}
