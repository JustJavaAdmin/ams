package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.SigningKeyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SigningKeyConfigRepository extends JpaRepository<SigningKeyConfig, Long> {
    Optional<SigningKeyConfig> findTopByOrderByIdAsc();
}
