package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.BankReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, Long> {
    List<BankReconciliation> findByOrganizationIdAndBankAccountIdOrderByStatementDateDesc(Long organizationId, Long bankAccountId);

    @Query("""
            select case when count(r) > 0 then true else false end
            from BankReconciliation r
            where r.organization.id = :organizationId
              and r.status in :statuses
              and r.statementDate between :startDate and :endDate
            """)
    boolean existsOpenWorkInPeriod(
            @Param("organizationId") Long organizationId,
            @Param("statuses") Collection<BankReconciliation.ReconciliationStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
