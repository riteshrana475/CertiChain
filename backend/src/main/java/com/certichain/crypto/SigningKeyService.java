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
        SigningKeyEntity saved = keys.findById(1).orElseGet(this::generate);
        cached = decode(saved.getPrivateKey(), saved.getPublicKey());
        return cached;
    }

    public String publicKeyId() {
        Material material = material();
        return material.configured() ? material.keyId() : "";
    }

    private SigningKeyEntity generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair pair = generator.generateKeyPair();
            SigningKeyEntity created = new SigningKeyEntity();
            created.setId(1);
            created.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            created.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            created.setKeyId(CanonicalHasher.sha256(pair.getPublic().getEncoded()).substring(0, 16));
            log.info("Created a local Ed25519 signing key {} in the database. The private key is not written to source control.", created.getKeyId());
            return keys.save(created);
        } catch (Exception exception) {
            throw new IllegalStateException("A signing key could not be created.", exception);
        }
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
}
