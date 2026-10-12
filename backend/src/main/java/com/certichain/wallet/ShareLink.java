package com.certichain.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share_links")
public class ShareLink {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "recipient_label", nullable = false)
    private String recipientLabel = "";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "max_access")
    private Integer maxAccess;

    @Column(name = "access_count", nullable = false)
    private int accessCount;

    @Column(name = "allow_download", nullable = false)
    private boolean allowDownload;

    @Column(name = "allow_preview", nullable = false)
    private boolean allowPreview = true;

    @Column(nullable = false)
    private String note = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getRecipientLabel() {
        return recipientLabel;
    }

    public void setRecipientLabel(String recipientLabel) {
        this.recipientLabel = recipientLabel;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Integer getMaxAccess() {
        return maxAccess;
    }

    public void setMaxAccess(Integer maxAccess) {
        this.maxAccess = maxAccess;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public boolean isAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public boolean isAllowPreview() {
        return allowPreview;
    }

    public void setAllowPreview(boolean allowPreview) {
        this.allowPreview = allowPreview;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean open(Instant now) {
        if (revokedAt != null || expiresAt.isBefore(now)) {
            return false;
        }
        return maxAccess == null || accessCount < maxAccess;
    }
}
