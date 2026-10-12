package com.certichain.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UploadScanTest {

    @Test
    void scannerResponsesAreNotTreatedAsCleanUnlessTheySayOk() {
        assertEquals("CLEAN", UploadScanner.interpret("stream: OK"));
        assertEquals("INFECTED", UploadScanner.interpret("stream: Eicar-Test-Signature FOUND"));
        assertEquals("SCAN_UNAVAILABLE", UploadScanner.interpret(""));
        assertEquals("SCAN_UNAVAILABLE", UploadScanner.interpret(null));
    }
}
