package com.justjava.ams.einvoicing.nrs.repository;

import com.justjava.ams.einvoicing.nrs.entity.EInvoiceSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EInvoiceSubmissionRepository extends JpaRepository<EInvoiceSubmission, Long> {
    List<EInvoiceSubmission> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);
}
