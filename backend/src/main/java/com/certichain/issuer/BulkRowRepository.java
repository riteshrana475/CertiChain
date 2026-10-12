package com.certichain.issuer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkRowRepository extends JpaRepository<BulkRow, UUID> {
    List<BulkRow> findByJobIdOrderByRowNumberAsc(UUID jobId);
}
