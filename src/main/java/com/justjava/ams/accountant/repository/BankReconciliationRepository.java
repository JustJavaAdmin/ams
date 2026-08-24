package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.BankReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, Long> {
    List<BankReconciliation> findByOrganizationIdAndBankAccountIdOrderByStatementDateDesc(Long organizationId, Long bankAccountId);
}
