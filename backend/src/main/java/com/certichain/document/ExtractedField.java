package com.certichain.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "extracted_fields")
public class ExtractedField {

    @Id
    private UUID id;

    @Column(name = "extraction_id", nullable = false)
    private UUID extractionId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "candidate_value", nullable = false)
    private String candidateValue = "";

    @Column(name = "corrected_value", nullable = false)
    private String correctedValue = "";

    @Column(nullable = false)
    private String confidence;

    @Column(name = "source_page", nullable = false)
    private int sourcePage = 1;

    @Column(nullable = false)
    private String method;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getExtractionId() { return extractionId; }
    public void setExtractionId(UUID extractionId) { this.extractionId = extractionId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getCandidateValue() { return candidateValue; }
    public void setCandidateValue(String candidateValue) { this.candidateValue = candidateValue; }
    public String getCorrectedValue() { return correctedValue; }
    public void setCorrectedValue(String correctedValue) { this.correctedValue = correctedValue; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public int getSourcePage() { return sourcePage; }
    public void setSourcePage(int sourcePage) { this.sourcePage = sourcePage; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
}
