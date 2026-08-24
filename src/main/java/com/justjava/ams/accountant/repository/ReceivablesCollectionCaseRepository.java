package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.ReceivablesCollectionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceivablesCollectionCaseRepository extends JpaRepository<ReceivablesCollectionCase, Long> {
    List<ReceivablesCollectionCase> findByOrganizationIdOrderByDaysOverdueDescIdDesc(Long organizationId);
    Optional<ReceivablesCollectionCase> findByCustomerInvoiceId(Long customerInvoiceId);
    List<ReceivablesCollectionCase> findByCustomerIdOrderByDaysOverdueDescIdDesc(Long customerId);
}
