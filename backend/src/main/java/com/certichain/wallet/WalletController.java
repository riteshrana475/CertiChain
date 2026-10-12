package com.certichain.wallet;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.certichain.document.DocumentIntelligenceService;

@RestController
public class WalletController {

    private final WalletService wallet;
    private final DocumentIntelligenceService intelligence;

    public WalletController(WalletService wallet, DocumentIntelligenceService intelligence) {
        this.wallet = wallet;
        this.intelligence = intelligence;
    }

    @GetMapping("/api/wallet/documents")
    public WalletService.WalletPage list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "NEWEST") String sort,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return wallet.library(q, category, source, sort, archived, page, size);
    }

    @PostMapping(value = "/api/wallet/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WalletService.WalletItem upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam(defaultValue = "") String issuerName,
            @RequestParam(defaultValue = "") String issueDate,
            @RequestParam(defaultValue = "") String expiryDate,
            @RequestParam(defaultValue = "") String credentialNumber,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "") String tags) throws Exception {
        return wallet.upload(
                file.getBytes(),
                title,
                category,
                issuerName,
                issueDate,
                expiryDate,
                credentialNumber,
                description,
                tags);
    }

    @GetMapping("/api/wallet/documents/{id}")
    public WalletService.WalletItem one(@PathVariable UUID id) {
        return detail(id);
    }

    @GetMapping("/api/wallet/documents/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable UUID id) {
        return file(id, false);
    }

    @GetMapping("/api/wallet/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        return file(id, true);
    }

    @DeleteMapping("/api/wallet/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        wallet.delete(id);
    }

    @PostMapping("/api/wallet/documents/{id}/archive")
    public WalletService.WalletItem archive(@PathVariable UUID id) {
        return wallet.archive(id);
    }

    @PostMapping("/api/wallet/documents/{id}/extract")
    public DocumentIntelligenceService.ExtractionView extract(@PathVariable UUID id) {
        return intelligence.extract(id);
    }

    @GetMapping("/api/wallet/documents/{id}/extraction")
    public DocumentIntelligenceService.ExtractionView extraction(@PathVariable UUID id) {
        return intelligence.latest(id);
    }

    @PostMapping("/api/wallet/documents/{id}/extraction/confirm")
    public DocumentIntelligenceService.ExtractionView confirm(
            @PathVariable UUID id, @RequestBody DocumentIntelligenceService.ConfirmRequest request) {
        return intelligence.confirm(id, request);
    }

    @GetMapping("/api/notifications")
    public java.util.List<AppNotification> notifications() {
        return wallet.notifications();
    }

    @PatchMapping("/api/notifications/{id}/read")
    public AppNotification read(@PathVariable UUID id) {
        return wallet.markRead(id);
    }

    @GetMapping("/api/wallet/activity")
    public java.util.List<AuditEvent> activity() {
        return wallet.activity();
    }

    private WalletService.WalletItem detail(UUID id) {
        var document = wallet.ownDocument(id);
        return new WalletService.WalletItem(
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

    private ResponseEntity<byte[]> file(UUID id, boolean attachment) {
        var document = wallet.ownDocument(id);
        byte[] bytes = wallet.bytes(document);
        String disposition = (attachment ? "attachment" : "inline") + "; filename=\"document\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .body(bytes);
    }
}
