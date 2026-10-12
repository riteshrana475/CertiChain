package com.certichain.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, UUID> {
    List<DocumentExtraction> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
