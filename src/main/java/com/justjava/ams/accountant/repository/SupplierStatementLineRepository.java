package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.SupplierStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierStatementLineRepository extends JpaRepository<SupplierStatementLine, Long> {
    List<SupplierStatementLine> findBySupplierStatementIdOrderByTransactionDateAscIdAsc(Long supplierStatementId);
}
