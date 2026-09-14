package com.moveiq.repository;

import com.moveiq.domain.TripEntity;
import com.moveiq.domain.TripKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<TripEntity, TripKey> {}
