package com.certichain.blockchain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainSettingsRepository extends JpaRepository<ChainSettingsEntity, Integer> {
}
