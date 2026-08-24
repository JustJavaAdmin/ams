package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.CustomerStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerStatementRepository extends JpaRepository<CustomerStatement, Long> {
    List<CustomerStatement> findByOrganizationIdOrderByStatementDateDescIdDesc(Long organizationId);
}
