package com.certichain.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_extractions")
public class DocumentExtraction {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String method;

    @Column(name = "raw_text", nullable = false)
    private String rawText = "";

    @Column(nullable = false)
    private String note = "";

    @Column(name = "suggested_category", nullable = false)
    private String suggestedCategory = "";

    @Column(name = "category_confidence", nullable = false)
    private String categoryConfidence = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getSuggestedCategory() { return suggestedCategory; }
    public void setSuggestedCategory(String suggestedCategory) { this.suggestedCategory = suggestedCategory; }
    public String getCategoryConfidence() { return categoryConfidence; }
    public void setCategoryConfidence(String categoryConfidence) { this.categoryConfidence = categoryConfidence; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
