package com.certichain.verification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationLogRepository extends JpaRepository<VerificationLogEntity, UUID> {

    List<VerificationLogEntity> findAllByOrderByVerifiedAtDesc();

    List<VerificationLogEntity> findByCertificateIdInOrderByVerifiedAtDesc(Collection<String> certificateIds);
}
