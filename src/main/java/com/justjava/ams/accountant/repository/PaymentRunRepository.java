package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.PaymentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRunRepository extends JpaRepository<PaymentRun, Long> {
    List<PaymentRun> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
