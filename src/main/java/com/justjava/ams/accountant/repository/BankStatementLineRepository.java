package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Long> {
    List<BankStatementLine> findByReconciliationIdOrderByTransactionDateAscIdAsc(Long reconciliationId);
    boolean existsByMatchedGeneralLedgerId(Long matchedGeneralLedgerId);
    boolean existsByMatchedGeneralLedgerIdAndIdNot(Long matchedGeneralLedgerId, Long id);
}
