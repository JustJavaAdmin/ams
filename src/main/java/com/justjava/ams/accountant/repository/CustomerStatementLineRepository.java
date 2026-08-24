package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.CustomerStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerStatementLineRepository extends JpaRepository<CustomerStatementLine, Long> {
    List<CustomerStatementLine> findByCustomerStatementIdOrderByTransactionDateAscIdAsc(Long customerStatementId);
}
