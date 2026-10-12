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
import org.springframework.stereotype.Component;
import java.security.spec.X509EncodedKeySpec;

@Component
public class CredentialSigner {

    private static final Logger log = LoggerFactory.getLogger(CredentialSigner.class);

    private final SigningKeyService keys;

    public CredentialSigner(SigningKeyService keys) {
        this.keys = keys;
    }

    public boolean configured() {
        return keys.material().configured();
    }

    public void apply(CertificateEntity certificate, String payload) {
        SigningKeyService.Material material = keys.material();
        if (!material.configured()) {
            certificate.setSignature("");
            certificate.setSigningKeyId("");
            certificate.setSignerPublicKey("");
            log.warn("No signing key is available. {} was stored without a detached signature.", certificate.getId());
            return;
        }
        certificate.setSignature(sign(material.privateKey(), payload));
        certificate.setSigningKeyId(material.keyId());
        certificate.setSignerPublicKey(material.publicKey());
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
