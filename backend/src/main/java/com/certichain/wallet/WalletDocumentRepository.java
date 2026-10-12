package com.certichain.wallet;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletDocumentRepository extends JpaRepository<WalletDocument, UUID> {

    long countByOwnerIdAndArchivedFalse(UUID ownerId);

    @Query("""
            select d from WalletDocument d
            where d.ownerId = :ownerId
            and (:archived is null or d.archived = :archived)
            and (:category = '' or d.category = :category)
            and (
                :q = ''
                or lower(d.title) like lower(concat('%', :q, '%'))
                or lower(d.issuerName) like lower(concat('%', :q, '%'))
                or lower(d.credentialNumber) like lower(concat('%', :q, '%'))
                or lower(d.tags) like lower(concat('%', :q, '%'))
            )
            """)
    Page<WalletDocument> search(
            @Param("ownerId") UUID ownerId,
            @Param("q") String q,
            @Param("category") String category,
            @Param("archived") Boolean archived,
            Pageable pageable);
}
