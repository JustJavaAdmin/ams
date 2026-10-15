package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.NrsIntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NrsIntegrationConfigRepository extends JpaRepository<NrsIntegrationConfig, Long> {
    Optional<NrsIntegrationConfig> findTopByOrderByIdAsc();
}
