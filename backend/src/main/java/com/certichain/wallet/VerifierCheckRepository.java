package com.certichain.wallet;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerifierCheckRepository extends JpaRepository<VerifierCheck, UUID> {

    List<VerifierCheck> findTop20ByUserIdOrderByCheckedAtDesc(UUID userId);
}
