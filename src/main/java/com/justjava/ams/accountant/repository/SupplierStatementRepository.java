package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.SupplierStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierStatementRepository extends JpaRepository<SupplierStatement, Long> {
    List<SupplierStatement> findByOrganizationIdOrderByStatementDateDescIdDesc(Long organizationId);
}
