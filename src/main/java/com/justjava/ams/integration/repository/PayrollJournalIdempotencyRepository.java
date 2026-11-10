package com.justjava.ams.integration.repository;

import com.justjava.ams.integration.entity.PayrollJournalIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollJournalIdempotencyRepository extends JpaRepository<PayrollJournalIdempotency, Long> {
    Optional<PayrollJournalIdempotency> findByOrganizationIdAndIdempotencyKey(
            Long organizationId, String idempotencyKey);
}
