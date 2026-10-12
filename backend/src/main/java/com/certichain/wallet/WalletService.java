package com.certichain.wallet;

import com.certichain.certificate.CertificateEntity;
import com.certichain.certificate.CertificateRepository;
import com.certichain.document.OcrClient;
import com.certichain.institution.InstitutionRepository;
import com.certichain.security.CurrentUser;
import com.certichain.student.StudentEntity;
import com.certichain.student.StudentRepository;
import com.certichain.user.UserAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletService {

    public static final String USER_UPLOADED = "USER_UPLOADED";

    private final CurrentUser current;
    private final WalletDocumentRepository documents;
    private final WalletObjectStorage storage;
    private final StudentRepository students;
    private final CertificateRepository certificates;
    private final InstitutionRepository institutions;
    private final ShareLinkRepository shares;
    private final ActivityRecorder activity;
    private final OcrClient ocr;
    private final AuditEventRepository audits;
    private final NotificationRepository notifications;
    private final UploadScanner scanner;

    public WalletService(
            CurrentUser current,
            WalletDocumentRepository documents,
            WalletObjectStorage storage,
            StudentRepository students,
            CertificateRepository certificates,
            InstitutionRepository institutions,
            ShareLinkRepository shares,
            ActivityRecorder activity,
            OcrClient ocr,
            AuditEventRepository audits,
            NotificationRepository notifications,
            UploadScanner scanner) {
        this.current = current;
        this.documents = documents;
        this.storage = storage;
        this.students = students;
        this.certificates = certificates;
        this.institutions = institutions;
        this.shares = shares;
        this.activity = activity;
        this.ocr = ocr;
        this.audits = audits;
        this.notifications = notifications;
        this.scanner = scanner;
    }

    public WalletPage library(String q, String category, String source, String sort, boolean archived, int page, int size) {
        UserAccount user = current.requireRole("STUDENT");
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 24);
        String query = q == null ? "" : q.trim();
        String categoryFilter = category == null ? "" : category.trim().toUpperCase();
        if ("ALL".equals(categoryFilter)) {
            categoryFilter = "";
        }
        List<WalletItem> credentials = "UPLOAD".equalsIgnoreCase(source) ? List.of() : credentialsFor(user, query);
        var uploads = documents.search(
                user.getId(),
                query,
                categoryFilter,
                archived,
                PageRequest.of(safePage, safeSize, sortOf(sort)));
        List<WalletItem> uploadItems = "CREDENTIAL".equalsIgnoreCase(source)
                ? List.of()
                : uploads.getContent().stream().map(this::uploadItem).toList();
        long uploadCount = documents.countByOwnerIdAndArchivedFalse(user.getId());
        long activeShares = shares.countByOwnerIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), Instant.now());
        return new WalletPage(
                credentials,
                uploadItems,
                uploads.getNumber(),
                uploads.getSize(),
                "CREDENTIAL".equalsIgnoreCase(source) ? 0 : uploads.getTotalElements(),
                new Summary(credentials.size() + uploadCount, credentials.size(), 0, activeShares));
    }

    public WalletItem upload(
            byte[] bytes,
            String title,
            String category,
            String issuerName,
            String issueDate,
            String expiryDate,
            String credentialNumber,
            String description,
            String tags) {
        UserAccount user = current.requireRole("STUDENT");
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file must be a PDF, PNG, or JPEG under 10 MB.");
        }
        String contentType;
        try {
            contentType = FileSignatures.contentType(bytes);
            category = FileSignatures.category(category);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a document title.");
        }
        String scanStatus = scanner.scan(bytes);
        String key = storage.put(bytes, contentType);
        WalletDocument document = new WalletDocument();
        document.setId(UUID.randomUUID());
        document.setOwnerId(user.getId());
        document.setTitle(title.trim());
        document.setCategory(category);
        document.setIssuerName(blank(issuerName));
        document.setStorageKey(key);
        document.setContentType(contentType);
        document.setFileSize(bytes.length);
        document.setSha256(WalletObjectStorage.sha256(bytes));
        document.setStatus(USER_UPLOADED);
        document.setScanStatus(scanStatus);
        document.setDescription(blank(description));
        try {
            document.setIssueDate(date(issueDate));
            document.setExpiryDate(date(expiryDate));
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use dates as YYYY-MM-DD.");
        }
        document.setCredentialNumber(blank(credentialNumber));
        document.setTags(blank(tags));
        document.setExtractedText(suggestText(bytes, contentType));
        document.setCreatedAt(Instant.now());
        documents.save(document);
        activity.audit(user.getEmail(), "DOCUMENT_UPLOADED", "DOCUMENT", document.getId().toString(), document.getTitle());
        activity.notify(user.getId(), "DOCUMENT_UPLOADED", "Document stored", document.getTitle() + " is in your wallet. It is not university-verified." + scanNote(scanStatus), "/wallet/documents/" + document.getId());
        return uploadItem(document);
    }

    public WalletDocument ownDocument(UUID id) {
        UserAccount user = current.requireRole("STUDENT");
        WalletDocument document = documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found."));
        if (!document.getOwnerId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found.");
        }
        return document;
    }

    public byte[] bytes(WalletDocument document) {
        return storage.get(document.getStorageKey());
    }

    public void delete(UUID id) {
        UserAccount user = current.require();
        WalletDocument document = ownDocument(id);
        documents.delete(document);
        activity.audit(user.getEmail(), "DOCUMENT_DELETED", "DOCUMENT", id.toString(), "Personal upload removed");
    }

    public WalletItem archive(UUID id) {
        WalletDocument document = ownDocument(id);
        document.setArchived(!document.isArchived());
        documents.save(document);
        return uploadItem(document);
    }

    public List<AppNotification> notifications() {
        return notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(current.require().getId());
    }

    public AppNotification markRead(UUID id) {
        UserAccount user = current.require();
        AppNotification notice = notifications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found."));
        if (!notice.getRecipientId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found.");
        }
        notice.setReadAt(Instant.now());
        return notifications.save(notice);
    }

    public List<AuditEvent> activity() {
        return audits.findTop20ByActorEmailOrderByCreatedAtDesc(current.require().getEmail());
    }

    private List<WalletItem> credentialsFor(UserAccount user, String query) {
        StudentEntity student = students.findByEmailIgnoreCase(user.getEmail()).orElse(null);
        if (student == null) {
            return List.of();
        }
        String issuer = institutions.findById(student.getInstitutionId()).map(item -> item.getName()).orElse("CertiChain");
        List<WalletItem> items = new ArrayList<>();
        for (CertificateEntity certificate : certificates.findByStudentId(student.getId())) {
            if ("DRAFT".equals(certificate.getStatus())) {
                continue;
            }
            String haystack = (certificate.getId() + " " + certificate.getDegree() + " " + issuer).toLowerCase();
            if (!query.isBlank() && !haystack.contains(query.toLowerCase())) {
                continue;
            }
            items.add(new WalletItem(
                    certificate.getId(),
                    "CREDENTIAL",
                    certificate.getDegree(),
                    "DEGREE",
                    "ISSUER",
                    issuer,
                    certificate.getIssueDate().toString(),
                    certificate.getStatus(),
                    certificate.getId(),
                    "",
                    0,
                    "",
                    ""));
        }
        return items;
    }

    private WalletItem uploadItem(WalletDocument document) {
        return new WalletItem(
                document.getId().toString(),
                "UPLOAD",
                document.getTitle(),
                document.getCategory(),
                "USER",
                document.getIssuerName(),
                document.getCreatedAt().toString(),
                document.getStatus(),
                document.getCredentialNumber(),
                document.getContentType(),
                document.getFileSize(),
                document.getExtractedText(),
                document.getScanStatus() == null ? "UNSCANNED" : document.getScanStatus());
    }

    private String suggestText(byte[] bytes, String contentType) {
        if (!contentType.startsWith("image/")) {
            return "";
        }
        try {
            String text = ocr.extract(bytes);
            return text == null ? "" : text.substring(0, Math.min(text.length(), 2000));
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static Sort sortOf(String sort) {
        return switch (sort == null ? "" : sort) {
            case "OLDEST" -> Sort.by("createdAt").ascending();
            case "NAME" -> Sort.by("title").ascending();
            case "NAME_DESC" -> Sort.by("title").descending();
            default -> Sort.by("createdAt").descending();
        };
    }

    private static String scanNote(String scanStatus) {
        return switch (scanStatus) {
            case "CLEAN" -> " The malware scanner reported the file clean.";
            case "SCAN_UNAVAILABLE" -> " The malware scanner did not answer, so the file was not marked clean.";
            default -> " No malware scanner is configured, so the file was not marked clean.";
        };
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    public record WalletItem(
            String id,
            String kind,
            String title,
            String category,
            String source,
            String issuer,
            String date,
            String status,
            String credentialId,
            String contentType,
            long fileSize,
            String extractedText,
            String scanStatus) {
    }

    public record Summary(long total, long issued, long pendingRequests, long activeShares) {
    }

    public record WalletPage(
            List<WalletItem> credentials,
            List<WalletItem> uploads,
            int page,
            int size,
            long uploadTotal,
            Summary summary) {
    }
}
