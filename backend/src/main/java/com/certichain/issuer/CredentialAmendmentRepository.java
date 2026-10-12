package com.certichain.issuer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialAmendmentRepository extends JpaRepository<CredentialAmendment, UUID> {
    List<CredentialAmendment> findByCertificateIdOrderByCreatedAtDesc(String certificateId);
}
