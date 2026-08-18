package com.commercecore.order.repository;

import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
               OR (status = 'PROCESSING' AND (
                   (next_retry_at IS NOT NULL AND next_retry_at <= :now)
                   OR (next_retry_at IS NULL AND claimed_at <= :claimCutoff)
               ))
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingEventsForUpdate(
            @Param("now") Instant now,
            @Param("claimCutoff") Instant claimCutoff,
            @Param("limit") int limit);

    long countByStatus(OutboxStatus status);
}
