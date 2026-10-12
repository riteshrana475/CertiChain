package com.certichain.wallet;

import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.security.CurrentUser;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import com.certichain.user.UserAccount;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CurrentUser current;
    private final ShareLinkRepository links;
    private final ShareItemRepository items;
    private final WalletDocumentRepository documents;
    private final CertificateRepository certificates;
    private final StudentRepository students;
    private final WalletObjectStorage storage;
    private final ActivityRecorder activity;
    private final ShareAccessRepository accessEvents;

    public ShareService(
            CurrentUser current,
            ShareLinkRepository links,
            ShareItemRepository items,
            WalletDocumentRepository documents,
            CertificateRepository certificates,
            StudentRepository students,
            WalletObjectStorage storage,
            ActivityRecorder activity,
            ShareAccessRepository accessEvents) {
        this.current = current;
        this.links = links;
        this.items = items;
        this.documents = documents;
        this.certificates = certificates;
        this.students = students;
        this.storage = storage;
        this.activity = activity;
        this.accessEvents = accessEvents;
    }

    public CreatedShare create(CreateShare request) {
        UserAccount user = current.requireRole("STUDENT");
        List<UUID> documentIds = request.documentIds() == null ? List.of() : request.documentIds();
        List<String> certificateIds = request.certificateIds() == null ? List.of() : request.certificateIds();
        if (documentIds.isEmpty() && certificateIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one document to share.");
        }
        Instant expires = expiry(request.expiresAt());
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        ShareLink link = new ShareLink();
        link.setId(UUID.randomUUID());
        link.setTokenHash(hash(token));
        link.setOwnerId(user.getId());
        link.setRecipientLabel(trim(request.recipient()));
        link.setExpiresAt(expires);
        link.setMaxAccess(request.maxAccess() != null && request.maxAccess() > 0 ? request.maxAccess() : null);
        boolean preview = request.allowPreview() == null || request.allowPreview();
        if (!preview && !request.allowDownload()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allow preview or download.");
        }
        link.setAllowPreview(preview);
        link.setAllowDownload(request.allowDownload());
        link.setNote(trim(request.note()));
        link.setCreatedAt(Instant.now());
        links.save(link);
        for (UUID documentId : documentIds) {
            WalletDocument document = documents.findById(documentId)
                    .filter(item -> item.getOwnerId().equals(user.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the documents is not in your wallet."));
            ShareItem item = new ShareItem();
            item.setId(UUID.randomUUID());
            item.setShareId(link.getId());
            item.setDocumentId(document.getId());
            items.save(item);
        }
        StudentEntity student = students.findByEmailIgnoreCase(user.getEmail()).orElse(null);
        for (String certificateId : certificateIds) {
            CertificateEntity certificate = certificates.findById(certificateId).orElse(null);
            if (certificate == null || student == null || !certificate.getStudentId().equals(student.getId()) || "DRAFT".equals(certificate.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can only share credentials issued to you.");
            }
            ShareItem item = new ShareItem();
            item.setId(UUID.randomUUID());
            item.setShareId(link.getId());
            item.setCertificateId(certificate.getId());
            items.save(item);
        }
        activity.audit(user.getEmail(), "SHARE_CREATED", "SHARE", link.getId().toString(), "Scoped share link");
        activity.notify(user.getId(), "SHARE_CREATED", "Share link created", "A link was created. Email delivery is not configured, so copy it yourself.", "/wallet/shared");
        return new CreatedShare(link.getId(), token, link.getExpiresAt().toString(), link.isAllowDownload());
    }

    public List<ShareView> mine() {
        UserAccount user = current.requireRole("STUDENT");
        return links.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream().map(this::view).toList();
    }

    public ShareView revoke(UUID id) {
        UserAccount user = current.requireRole("STUDENT");
        ShareLink link = links.findById(id)
                .filter(item -> item.getOwnerId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link was not found."));
        link.setRevokedAt(Instant.now());
        links.save(link);
        activity.audit(user.getEmail(), "SHARE_REVOKED", "SHARE", link.getId().toString(), "Access revoked");
        return view(link);
    }

    public SharedBundle open(String token) {
        ShareLink link = usable(token);
        if (!link.isAllowPreview()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This link does not allow viewing.");
        }
        link.setAccessCount(link.getAccessCount() + 1);
        links.save(link);
        record(link, "OPENED");
        return new SharedBundle(link.getExpiresAt().toString(), link.isAllowDownload(), link.getRecipientLabel(), view(link).items());
    }

    public DownloadFile download(String token, UUID documentId) {
        ShareLink link = usable(token);
        if (!link.isAllowDownload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This link allows preview only.");
        }
        boolean included = items.findByShareId(link.getId()).stream()
                .anyMatch(item -> documentId.equals(item.getDocumentId()));
        if (!included) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That document is not on this link.");
        }
        record(link, "DOWNLOADED");
        WalletDocument document = documents.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That document is not on this link."));
        return new DownloadFile(document.getTitle(), document.getContentType(), storage.get(document.getStorageKey()));
    }

    static String hash(String token) {
        return com.certichain.crypto.CanonicalHasher.sha256(token);
    }

    private ShareLink usable(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not available.");
        }
        ShareLink link = links.findByTokenHash(hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This link is not available."));
        if (!link.open(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This link has expired or was revoked.");
        }
        return link;
    }

    private ShareView view(ShareLink link) {
        List<SharedItem> shared = new ArrayList<>();
        for (ShareItem item : items.findByShareId(link.getId())) {
            if (item.getDocumentId() != null) {
                documents.findById(item.getDocumentId()).ifPresent(document -> shared.add(new SharedItem(
                        document.getId().toString(), "UPLOAD", document.getTitle(), document.getStatus(), document.getCategory())));
            }
            if (item.getCertificateId() != null) {
                certificates.findById(item.getCertificateId()).ifPresent(certificate -> shared.add(new SharedItem(
                        certificate.getId(), "CREDENTIAL", certificate.getDegree(), certificate.getStatus(), "DEGREE")));
            }
        }
        String status = link.getRevokedAt() != null ? "REVOKED" : link.getExpiresAt().isBefore(Instant.now()) ? "EXPIRED" : "ACTIVE";
        return new ShareView(link.getId().toString(), link.getRecipientLabel(), link.getExpiresAt().toString(), status, link.isAllowDownload(), link.getCreatedAt().toString(), shared);
    }

    private static Instant expiry(String value) {
        Instant expires;
        try {
            expires = value == null || value.isBlank()
                    ? Instant.now().plus(7, ChronoUnit.DAYS)
                    : Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an expiry time.");
        }
        if (!expires.isAfter(Instant.now()) || expires.isAfter(Instant.now().plus(90, ChronoUnit.DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry must be within the next 90 days.");
        }
        return expires;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public List<ShareAccessEvent> history(UUID id) {
        UserAccount user = current.requireRole("STUDENT");
        ShareLink link = links.findById(id)
                .filter(item -> item.getOwnerId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link was not found."));
        return accessEvents.findTop50ByShareIdOrderByCreatedAtDesc(link.getId());
    }

    private void record(ShareLink link, String type) {
        ShareAccessEvent event = new ShareAccessEvent();
        event.setId(UUID.randomUUID());
        event.setShareId(link.getId());
        event.setEventType(type);
        event.setCreatedAt(Instant.now());
        accessEvents.save(event);
        usersNotify(link, type);
    }

    private void usersNotify(ShareLink link, String type) {
        activity.notify(link.getOwnerId(), "SHARE_ACCESS", "Share link used", "Someone " + ("DOWNLOADED".equals(type) ? "downloaded a file from" : "opened") + " a share link. The file contents are not included here.", "/wallet/shared");
    }

    public record CreateShare(
            List<UUID> documentIds,
            List<String> certificateIds,
            String recipient,
            String expiresAt,
            Integer maxAccess,
            boolean allowDownload,
            Boolean allowPreview,
            String note) {
    }

    public record CreatedShare(UUID id, String token, String expiresAt, boolean allowDownload) {
    }

    public record SharedItem(String id, String kind, String title, String status, String category) {
    }

    public record ShareView(
            String id,
            String recipient,
            String expiresAt,
            String status,
            boolean allowDownload,
            String createdAt,
            List<SharedItem> items) {
    }

    public record SharedBundle(String expiresAt, boolean allowDownload, String recipient, List<SharedItem> items) {
    }

    public record DownloadFile(String title, String contentType, byte[] bytes) {
    }
}
