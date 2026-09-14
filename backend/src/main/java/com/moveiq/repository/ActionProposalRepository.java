package com.moveiq.repository;

import com.moveiq.domain.ActionProposalEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionProposalRepository extends JpaRepository<ActionProposalEntity, UUID> {}
