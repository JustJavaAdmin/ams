package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.DepreciationJournalImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepreciationJournalImportRepository extends JpaRepository<DepreciationJournalImport, Long> {
    Optional<DepreciationJournalImport> findByOrganizationIdAndExternalBatchIdIgnoreCase(Long organizationId, String externalBatchId);
    Optional<DepreciationJournalImport> findByManualJournalId(Long manualJournalId);
    List<DepreciationJournalImport> findByOrganizationIdOrderByImportedAtDesc(Long organizationId);
}
