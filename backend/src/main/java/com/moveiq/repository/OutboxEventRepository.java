package com.moveiq.repository;

import com.moveiq.domain.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM moveiq.outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextBatch();
}
