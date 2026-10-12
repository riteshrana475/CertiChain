package com.certichain.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {
    List<ExtractedField> findByExtractionId(UUID extractionId);
}
