package com.justjava.ams.invoicing.delivery.repository;

import com.justjava.ams.invoicing.delivery.entity.InvoiceDispatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceDispatchRepository extends JpaRepository<InvoiceDispatch, Long> {
    List<InvoiceDispatch> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);
}
