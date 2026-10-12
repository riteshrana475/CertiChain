package com.certichain.issuer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credential_amendments")
public class CredentialAmendment {
    @Id
    private UUID id;
    @Column(name = "certificate_id", nullable = false)
    private String certificateId;
    @Column(name = "actor_email", nullable = false)
    private String actorEmail = "";
    @Column(nullable = false)
    private String reason;
    @Column(name = "previous_hash", nullable = false)
    private String previousHash = "";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCertificateId() { return certificateId; }
    public void setCertificateId(String certificateId) { this.certificateId = certificateId; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
