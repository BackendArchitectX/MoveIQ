package com.moveiq.repository;

import com.moveiq.domain.ActionProposalEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionProposalRepository extends JpaRepository<ActionProposalEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ActionProposalEntity p where p.id = :id")
    Optional<ActionProposalEntity> findForUpdate(@Param("id") UUID id);
}
