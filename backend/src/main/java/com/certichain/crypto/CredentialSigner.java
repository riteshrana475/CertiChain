package com.certichain.crypto;

import com.certichain.certificate.CertificateEntity;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
public class CredentialSigner {

    private static final Logger log = LoggerFactory.getLogger(CredentialSigner.class);

    private final boolean configured;
    private final PrivateKey privateKey;
    private final String publicKey;
    private final String keyId;

    public CredentialSigner(
            @Value("${certichain.signing.private-key:}") String encodedPrivateKey,
            @Value("${certichain.signing.public-key:}") String encodedPublicKey) {
        PrivateKey loadedPrivate = null;
        String loadedPublic = "";
        String loadedId = "";
        boolean ready = false;
        if (encodedPrivateKey != null && !encodedPrivateKey.isBlank() && encodedPublicKey != null && !encodedPublicKey.isBlank()) {
            try {
                loadedPrivate = KeyFactory.getInstance("Ed25519")
                        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encodedPrivateKey.trim())));
                PublicKey parsed = KeyFactory.getInstance("Ed25519")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encodedPublicKey.trim())));
                loadedPublic = Base64.getEncoder().encodeToString(parsed.getEncoded());
                loadedId = CanonicalHasher.sha256(parsed.getEncoded()).substring(0, 16);
                ready = true;
            } catch (Exception exception) {
                log.warn("Signing key was not loaded. New credentials will not receive a detached signature. {}", exception.getMessage());
            }
        }
        this.configured = ready;
        this.privateKey = loadedPrivate;
        this.publicKey = loadedPublic;
        this.keyId = loadedId;
    }

    public boolean configured() {
        return configured;
    }

    public void apply(CertificateEntity certificate, String payload) {
        if (!configured) {
            certificate.setSignature("");
            certificate.setSigningKeyId("");
            certificate.setSignerPublicKey("");
            return;
        }
        certificate.setSignature(sign(privateKey, payload));
        certificate.setSigningKeyId(keyId);
        certificate.setSignerPublicKey(publicKey);
    }

    public String check(String payload, String signatureValue, String storedPublicKey) {
        if (signatureValue == null || signatureValue.isBlank()) {
            return "NOT_APPLICABLE";
        }
        if (storedPublicKey == null || storedPublicKey.isBlank()) {
            return "UNAVAILABLE";
        }
        try {
            return verify(payload, signatureValue, storedPublicKey) ? "PASS" : "FAIL";
        } catch (RuntimeException exception) {
            return "FAIL";
        }
    }

    public static String payload(String canonicalFields, String documentHash) {
        return canonicalFields + "|" + (documentHash == null ? "" : documentHash);
    }

    public static String sign(PrivateKey privateKey, String payload) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("The credential could not be signed.", exception);
        }
    }

    public static boolean verify(String payload, String signatureValue, String storedPublicKey) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(storedPublicKey)));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureValue));
        } catch (Exception exception) {
            return false;
        }
    }
}
