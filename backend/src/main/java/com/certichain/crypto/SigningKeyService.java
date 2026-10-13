package com.certichain.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Service
public class SigningKeyService {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyService.class);

    private final SigningKeyRepository keys;
    private final String envPrivate;
    private final String envPublic;
    private Material cached;

    public SigningKeyService(
            SigningKeyRepository keys,
            @Value("${certichain.signing.private-key:}") String envPrivate,
            @Value("${certichain.signing.public-key:}") String envPublic) {
        this.keys = keys;
        this.envPrivate = envPrivate == null ? "" : envPrivate.trim();
        this.envPublic = envPublic == null ? "" : envPublic.trim();
    }

    public synchronized Material material() {
        if (cached != null) {
            return cached;
        }
        if (!envPrivate.isBlank() && !envPublic.isBlank()) {
            cached = decode(envPrivate, envPublic);
            if (!cached.configured()) {
                log.warn("The configured signing key could not be read. New credentials will not be signed until it is fixed.");
            }
            return cached;
        }
        SigningKeyEntity saved = keys.findById(1).filter(SigningKeyEntity::isSupplied).orElse(null);
        if (saved == null) {
            log.info("No university signing key is configured. New credentials are not signed. Signatures already stored on credentials still verify.");
            cached = new Material(false, null, "", "");
            return cached;
        }
        cached = decode(saved.getPrivateKey(), saved.getPublicKey());
        return cached;
    }

    public synchronized String install(String privateKey, String publicKey) {
        Material parsed = decode(privateKey, publicKey);
        if (!parsed.configured() || !pairMatches(parsed)) {
            throw new IllegalArgumentException("The signing key could not be read. Paste a base64 PKCS8 private key and the matching X509 public key.");
        }
        SigningKeyEntity saved = keys.findById(1).orElseGet(SigningKeyEntity::new);
        saved.setId(1);
        saved.setPrivateKey(privateKey.trim());
        saved.setPublicKey(parsed.publicKey());
        saved.setKeyId(parsed.keyId());
        saved.setSupplied(true);
        keys.save(saved);
        cached = parsed;
        log.info("Stored university signing key {}. The private key is not returned by the API.", parsed.keyId());
        return parsed.keyId();
    }

    public synchronized CreatedKey create() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair pair = generator.generateKeyPair();
            String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            String keyId = install(privateKey, publicKey);
            return new CreatedKey(keyId, publicKey, privateKey);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("A signing key could not be created.", exception);
        }
    }

    public String publicKeyId() {
        Material material = material();
        return material.configured() ? material.keyId() : "";
    }

    private static boolean pairMatches(Material material) {
        String payload = "certichain-signing-key";
        String signature = CredentialSigner.sign(material.privateKey(), payload);
        return CredentialSigner.verify(payload, signature, material.publicKey());
    }

    private static Material decode(String privateKey, String publicKey) {
        try {
            PrivateKey parsed = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            byte[] publicBytes = Base64.getDecoder().decode(publicKey);
            KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicBytes));
            return new Material(true, parsed, publicKey, CanonicalHasher.sha256(publicBytes).substring(0, 16));
        } catch (Exception exception) {
            return new Material(false, null, "", "");
        }
    }

    public record Material(boolean configured, PrivateKey privateKey, String publicKey, String keyId) {
    }

    public record CreatedKey(String keyId, String publicKey, String privateKey) {
    }
}
