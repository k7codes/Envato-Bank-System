package com.envatobank.api.transfer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByReference(String reference);

    Optional<Transfer> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);

    Page<Transfer> findBySourceAccountIdOrTargetAccountId(UUID sourceAccountId, UUID targetAccountId,
            Pageable pageable);

    Page<Transfer> findByCreatedBy(UUID createdBy, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amountMinor), 0)
            from Transfer t
            where t.sourceAccountId = :accountId
              and t.status = com.envatobank.api.transfer.TransferStatus.COMPLETED
              and t.createdAt >= :from
            """)
    long sumCompletedSince(@Param("accountId") UUID accountId, @Param("from") Instant from);
}
