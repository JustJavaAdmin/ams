package com.justjava.ams.einvoicing.archive.repository;

import com.justjava.ams.einvoicing.archive.entity.EInvoicePayloadArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EInvoicePayloadArchiveRepository extends JpaRepository<EInvoicePayloadArchive, Long> {
    Optional<EInvoicePayloadArchive> findBySubmissionId(Long submissionId);
}
