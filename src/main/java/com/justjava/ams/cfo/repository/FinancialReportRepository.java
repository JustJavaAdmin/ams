package com.justjava.ams.cfo.repository;

import com.justjava.ams.cfo.entity.FinancialReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialReportRepository extends JpaRepository<FinancialReport, Long> {
    Optional<FinancialReport> findByOrganizationIdAndReportName(Long organizationId, String reportName);
    List<FinancialReport> findByOrganizationIdAndReportType(Long organizationId, FinancialReport.ReportType reportType);
    List<FinancialReport> findByOrganizationIdAndStatus(Long organizationId, FinancialReport.ReportStatus status);
    List<FinancialReport> findByOrganizationIdAndReportDateBetween(Long organizationId, LocalDate fromDate, LocalDate toDate);

    // List reports for an organization, newest first
    List<FinancialReport> findByOrganizationIdOrderByReportDateDesc(Long organizationId);

    @Query("""
            select case when count(r) > 0 then true else false end
            from FinancialReport r
            where r.organization.id = :organizationId
              and r.status in :statuses
              and r.fromDate <= :endDate
              and r.toDate >= :startDate
            """)
    boolean existsOpenWorkOverlappingPeriod(
            @Param("organizationId") Long organizationId,
            @Param("statuses") Collection<FinancialReport.ReportStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

