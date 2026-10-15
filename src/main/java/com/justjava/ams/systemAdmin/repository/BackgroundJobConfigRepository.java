package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.BackgroundJobConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BackgroundJobConfigRepository extends JpaRepository<BackgroundJobConfig, Long> {
    Optional<BackgroundJobConfig> findByJobName(String jobName);
}
