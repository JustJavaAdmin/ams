package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.StorageRetentionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorageRetentionConfigRepository extends JpaRepository<StorageRetentionConfig, Long> {
    Optional<StorageRetentionConfig> findTopByOrderByIdAsc();
}
