package com.certichain.crypto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signing_keys")
public class SigningKeyEntity {

    @Id
    private int id;

    @Column(name = "private_key", nullable = false)
    private String privateKey;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(nullable = false)
    private boolean supplied;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public boolean isSupplied() {
        return supplied;
    }

    public void setSupplied(boolean supplied) {
        this.supplied = supplied;
    }
}
