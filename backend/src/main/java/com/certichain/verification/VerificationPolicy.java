package com.certichain.verification;

import java.util.ArrayList;
import java.util.List;

public final class VerificationPolicy {

    private VerificationPolicy() {
    }

    public static Report assess(Facts facts) {
        List<Check> checks = new ArrayList<>();
        checks.add(check(
                "Credential record",
                facts.found() ? "PASS" : "FAIL",
                facts.found() ? "A credential with this ID is in the issuer registry." : "No issued credential uses this ID."));
        checks.add(check(
                "Issuer registry",
                facts.found() ? "PASS" : "FAIL",
                facts.found()
                        ? "The record belongs to an institution registered in CertiChain. That is not an external accreditation."
                        : "There is no issuer record to trust."));
        checks.add(check("Detached signature", facts.signatureStatus(), signatureExplanation(facts.signatureStatus())));
        checks.add(check("File fingerprint", fileStatus(facts.fileHash()), fileExplanation(facts.fileHash())));
        checks.add(check(
                "Revocation",
                !facts.found() ? "NOT_APPLICABLE" : facts.revoked() ? "FAIL" : "PASS",
                !facts.found() ? "Revocation cannot be checked without a record." : facts.revoked() ? "The issuer revoked this credential." : "The issuer has not revoked this credential."));
        checks.add(check(
                "Expiry",
                !facts.found() || !facts.hasExpiry() ? "NOT_APPLICABLE" : facts.expired() ? "FAIL" : "PASS",
                !facts.found() || !facts.hasExpiry()
                        ? "This credential has no expiry date."
                        : facts.expired() ? "The expiry date on the record has passed." : "The expiry date has not passed."));
        checks.add(check("Chain anchor", anchorStatus(facts.anchor()), anchorExplanation(facts.anchor())));
        return new Report(overall(facts), checks);
    }

    public static String overall(Facts facts) {
        if (!facts.found()) {
            return "NOT_FOUND";
        }
        if (facts.revoked()) {
            return "REVOKED";
        }
        if (facts.hasExpiry() && facts.expired()) {
            return "EXPIRED";
        }
        if ("FAIL".equals(facts.signatureStatus())) {
            return "INVALID";
        }
        if ("UNAVAILABLE".equals(facts.signatureStatus())) {
            return "UNVERIFIABLE";
        }
        if ("MISMATCH".equals(facts.fileHash())) {
            return "HASH_MISMATCH";
        }
        return "VERIFIED";
    }

    private static String fileStatus(String fileHash) {
        return switch (fileHash) {
            case "MATCH" -> "PASS";
            case "MISMATCH" -> "FAIL";
            default -> "NOT_APPLICABLE";
        };
    }

    private static String fileExplanation(String fileHash) {
        return switch (fileHash) {
            case "MATCH" -> "The compared file matches the registered SHA-256 fingerprint. A matching hash does not by itself prove who issued it.";
            case "MISMATCH" -> "The compared file does not match the registered fingerprint.";
            default -> "No file was compared on this check.";
        };
    }

    private static String signatureExplanation(String status) {
        return switch (status) {
            case "PASS" -> "The detached signature matches the canonical credential fields and fingerprint for the stored issuer key.";
            case "FAIL" -> "The detached signature does not match the canonical credential fields.";
            case "UNAVAILABLE" -> "A signature is stored, but its public key is missing, so this check cannot pass.";
            default -> "No detached signature is stored. Authenticity rests on the issuer record. A hash alone is not a signature.";
        };
    }

    private static String anchorStatus(String anchor) {
        return switch (anchor) {
            case "ANCHORED" -> "PASS";
            case "UNAVAILABLE" -> "UNAVAILABLE";
            default -> "WARNING";
        };
    }

    private static String anchorExplanation(String anchor) {
        return switch (anchor) {
            case "ANCHORED" -> "A fingerprint is recorded on the configured chain. Anchoring shows the fingerprint was submitted, not that the certificate was genuinely issued.";
            case "UNAVAILABLE" -> "The chain could not be queried. The local credential record is unchanged.";
            default -> "This credential is not currently anchored. That is separate from whether the issuer record exists.";
        };
    }

    private static Check check(String name, String status, String explanation) {
        return new Check(name, status, explanation);
    }

    public record Facts(
            boolean found,
            boolean revoked,
            boolean hasExpiry,
            boolean expired,
            String signatureStatus,
            String fileHash,
            String anchor) {
    }

    public record Check(String name, String status, String explanation) {
    }

    public record Report(String overall, List<Check> checks) {
    }
}
