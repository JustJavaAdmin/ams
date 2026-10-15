package com.justjava.ams.systemAdmin.repository;

import com.justjava.ams.systemAdmin.entity.ApiClientRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiClientRegistrationRepository extends JpaRepository<ApiClientRegistration, Long> {
    Optional<ApiClientRegistration> findByClientId(String clientId);
}
