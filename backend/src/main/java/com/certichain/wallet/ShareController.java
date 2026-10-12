package com.certichain.wallet;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShareController {

    private final ShareService shares;

    public ShareController(ShareService shares) {
        this.shares = shares;
    }

    @PostMapping("/api/shares")
    public ShareService.CreatedShare create(@RequestBody ShareService.CreateShare request) {
        return shares.create(request);
    }

    @GetMapping("/api/shares")
    public java.util.List<ShareService.ShareView> list() {
        return shares.mine();
    }

    @PostMapping("/api/shares/{id}/revoke")
    public ShareService.ShareView revoke(@PathVariable UUID id) {
        return shares.revoke(id);
    }

    @GetMapping("/api/shares/{id}/access")
    public java.util.List<ShareAccessEvent> history(@PathVariable UUID id) {
        return shares.history(id);
    }

    @GetMapping("/api/shared/{token}")
    public ShareService.SharedBundle open(@PathVariable String token) {
        return shares.open(token);
    }

    @GetMapping("/api/shared/{token}/documents/{documentId}")
    public ResponseEntity<byte[]> download(@PathVariable String token, @PathVariable UUID documentId) {
        ShareService.DownloadFile file = shares.download(token, documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }
}
