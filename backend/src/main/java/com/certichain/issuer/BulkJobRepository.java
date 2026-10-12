package com.certichain.issuer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkJobRepository extends JpaRepository<BulkJob, UUID> {
}
