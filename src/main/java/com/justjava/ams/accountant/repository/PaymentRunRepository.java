package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.PaymentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentRunRepository extends JpaRepository<PaymentRun, Long> {
    List<PaymentRun> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    @Query("""
            select case when count(r) > 0 then true else false end
            from PaymentRun r
            where r.organization.id = :organizationId
              and r.status in :statuses
              and r.runDate between :startDate and :endDate
            """)
    boolean existsOpenWorkInPeriod(
            @Param("organizationId") Long organizationId,
            @Param("statuses") Collection<PaymentRun.PaymentRunStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
