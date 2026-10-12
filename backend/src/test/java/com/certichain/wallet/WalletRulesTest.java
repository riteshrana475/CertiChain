package com.certichain.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class WalletRulesTest {

    @Test
    void acceptsPdfPngAndJpegSignaturesOnly() {
        assertEquals("application/pdf", FileSignatures.contentType("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0};
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
        assertEquals("image/png", FileSignatures.contentType(png));
        assertEquals("image/jpeg", FileSignatures.contentType(jpeg));
        assertThrows(IllegalArgumentException.class, () -> FileSignatures.contentType("GIF8".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> FileSignatures.contentType(new byte[] {0, 1, 2}));
    }

    @Test
    void oversizedBytesAreNotAValidSignatureByThemselves() {
        assertThrows(IllegalArgumentException.class, () -> FileSignatures.category("VERIFIED"));
        assertEquals("DEGREE", FileSignatures.category(" degree "));
    }

    @Test
    void shareTokenIsStoredAsAHashAndClosedLinksStayClosed() {
        String token = "not-the-database-value";
        assertNotEquals(token, ShareService.hash(token));
        assertEquals(ShareService.hash(token), ShareService.hash(token));

        ShareLink link = new ShareLink();
        link.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        assertFalse(link.open(Instant.now()));

        link.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        link.setRevokedAt(Instant.now());
        assertFalse(link.open(Instant.now()));

        link.setRevokedAt(null);
        link.setMaxAccess(1);
        link.setAccessCount(1);
        assertFalse(link.open(Instant.now()));

        link.setAccessCount(0);
        assertTrue(link.open(Instant.now()));
    }

    @Test
    void uploadedStatusIsNotAnIssuerVerification() {
        assertNotEquals("VERIFIED", WalletService.USER_UPLOADED);
        assertNotEquals("ISSUED", WalletService.USER_UPLOADED);
    }
}
