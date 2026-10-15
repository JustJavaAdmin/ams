package com.justjava.ams.invoicing.document.repository;

import com.justjava.ams.invoicing.document.entity.InvoiceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceDocumentRepository extends JpaRepository<InvoiceDocument, Long> {
    List<InvoiceDocument> findByInvoiceTypeAndInvoiceIdOrderByGeneratedAtDesc(
            InvoiceDocument.InvoiceType invoiceType,
            Long invoiceId);
}
