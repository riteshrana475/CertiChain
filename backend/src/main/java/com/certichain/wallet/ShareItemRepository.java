package com.certichain.wallet;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareItemRepository extends JpaRepository<ShareItem, UUID> {

    List<ShareItem> findByShareId(UUID shareId);
}
