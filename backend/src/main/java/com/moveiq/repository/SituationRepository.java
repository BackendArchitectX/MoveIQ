package com.moveiq.repository;

import com.moveiq.domain.SituationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SituationRepository extends JpaRepository<SituationEntity, UUID> {
    Optional<SituationEntity> findByCorrelationKey(String correlationKey);
}
