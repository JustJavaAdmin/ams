package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.CustomerInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {
    Optional<CustomerInvoice> findByOrganizationIdAndInvoiceNumber(Long organizationId, String invoiceNumber);
    List<CustomerInvoice> findByOrganizationIdAndStatus(Long organizationId, CustomerInvoice.InvoiceStatus status);
    List<CustomerInvoice> findByOrganizationId(Long organizationId);
    List<CustomerInvoice> findByOrganizationIdAndInvoiceDateBetween(Long organizationId, LocalDate startDate, LocalDate endDate);
    boolean existsByOrganizationIdAndStatusIn(Long organizationId, Collection<CustomerInvoice.InvoiceStatus> statuses);

    @Query("""
            select case when count(i) > 0 then true else false end
            from CustomerInvoice i
            where i.organization.id = :organizationId
              and i.status in :statuses
              and i.invoiceDate between :startDate and :endDate
            """)
    boolean existsOpenWorkInPeriod(
            @Param("organizationId") Long organizationId,
            @Param("statuses") Collection<CustomerInvoice.InvoiceStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

