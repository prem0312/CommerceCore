package com.commercecore.payment.repository;

import com.commercecore.payment.model.OutboxEvent;
import com.commercecore.payment.model.OutboxStatus;
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
               OR (status = 'PROCESSING' AND (next_retry_at IS NULL OR next_retry_at <= :now))
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingEventsForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    long countByStatus(OutboxStatus status);
}
