package com.certichain.crypto;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKeyEntity, Integer> {
}
