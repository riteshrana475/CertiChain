package com.certichain.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.certichain.crypto.CredentialSigner;
import com.certichain.document.FieldExtractor;
import com.certichain.issuer.BulkCsv;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AdvancedRulesTest {

    @Test
    void extractsLabeledFieldsAndSuggestsACategory() {
        String text = """
                Student name: Vanshika Nimwal
                Institution: CertiChain
                Degree: B.Tech Computer Science
                Issue date: 2026-09-30
                This degree was conferred.
                """;
        var fields = FieldExtractor.parse(text);
        assertEquals("Vanshika Nimwal", fields.stream().filter(field -> field.name().equals("studentName")).findFirst().orElseThrow().value());
        assertEquals("DEGREE", FieldExtractor.suggestCategory(text).category());
        assertEquals("HIGH", FieldExtractor.suggestCategory(text).confidence());
        assertEquals("LOW", FieldExtractor.suggestCategory("miscellaneous note").confidence());
    }

    @Test
    void verificationOverallFollowsConservativeRules() {
        assertEquals("NOT_FOUND", VerificationPolicy.overall(facts(false, false, false, false, "NOT_APPLICABLE", "NOT_COMPARED", "NOT_ANCHORED")));
        assertEquals("REVOKED", VerificationPolicy.overall(facts(true, true, false, false, "PASS", "MATCH", "ANCHORED")));
        assertEquals("EXPIRED", VerificationPolicy.overall(facts(true, false, true, true, "PASS", "NOT_COMPARED", "ANCHORED")));
        assertEquals("INVALID", VerificationPolicy.overall(facts(true, false, false, false, "FAIL", "MATCH", "ANCHORED")));
        assertEquals("UNVERIFIABLE", VerificationPolicy.overall(facts(true, false, false, false, "UNAVAILABLE", "MATCH", "ANCHORED")));
        assertEquals("HASH_MISMATCH", VerificationPolicy.overall(facts(true, false, false, false, "NOT_APPLICABLE", "MISMATCH", "ANCHORED")));
        assertEquals("VERIFIED", VerificationPolicy.overall(facts(true, false, false, false, "NOT_APPLICABLE", "NOT_COMPARED", "NOT_ANCHORED")));
        assertEquals("WARNING", VerificationPolicy.assess(facts(true, false, false, false, "NOT_APPLICABLE", "NOT_COMPARED", "NOT_ANCHORED"))
                .checks().stream().filter(check -> check.name().equals("Chain anchor")).findFirst().orElseThrow().status());
    }

    @Test
    void signatureBindsThePayloadAndRejectsAChangedField() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String payload = CredentialSigner.payload("CERT-1|Ada|B.Tech|2026-01-01|8.0|CertiChain", "abc");
        String signature = CredentialSigner.sign(pair.getPrivate(), payload);
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        assertTrue(CredentialSigner.verify(payload, signature, publicKey));
        assertFalse(CredentialSigner.verify(payload + "x", signature, publicKey));
        assertFalse(CredentialSigner.verify(payload, signature, Base64.getEncoder().encodeToString("not-a-key".getBytes())));
    }

    @Test
    void csvCellsThatLookLikeFormulasAreEscaped() {
        assertEquals("'=cmd", BulkCsv.excelSafe("=cmd"));
        assertEquals("CC2022001", BulkCsv.excelSafe("CC2022001"));
        assertEquals(1, BulkCsv.parse("studentNumber,email,name,degree,certificateType,issueDate,grade,certificateId\nCC2022001,a@b.c,Ada,B.Tech,Degree,2026-01-01,8.0,\n").size());
    }

    private static VerificationPolicy.Facts facts(
            boolean found, boolean revoked, boolean hasExpiry, boolean expired, String signature, String file, String anchor) {
        return new VerificationPolicy.Facts(found, revoked, hasExpiry, expired, signature, file, anchor);
    }
}
