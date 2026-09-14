package com.moveiq.repository;

import com.moveiq.domain.SituationContributionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SituationContributionRepository extends JpaRepository<SituationContributionEntity, UUID> {
    boolean existsBySituationIdAndSourceEventId(UUID situationId, String sourceEventId);
}
