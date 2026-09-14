package com.moveiq.repository;

import com.moveiq.domain.ActionExecutionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionExecutionRepository extends JpaRepository<ActionExecutionEntity, UUID> {
    Optional<ActionExecutionEntity> findByIdempotencyKey(String idempotencyKey);
}
