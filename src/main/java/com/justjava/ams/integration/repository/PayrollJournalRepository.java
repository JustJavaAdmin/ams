package com.justjava.ams.integration.repository;

import com.justjava.ams.integration.entity.PayrollJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollJournalRepository extends JpaRepository<PayrollJournal, Long> {
    Optional<PayrollJournal> findByOrganizationIdAndSourceSystemAndExternalReferenceAndRevision(
            Long organizationId, String sourceSystem, String externalReference, String revision);
}
