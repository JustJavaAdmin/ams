package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.WebhookPlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookPlatformConfigRepository extends JpaRepository<WebhookPlatformConfig, Long> {
    Optional<WebhookPlatformConfig> findTopByOrderByIdAsc();
}
