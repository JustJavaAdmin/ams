package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.PurchaseInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    Optional<PurchaseInvoice> findByOrganizationIdAndPurchaseOrderNumber(Long organizationId, String purchaseOrderNumber);
    List<PurchaseInvoice> findByOrganizationId(Long organizationId);
    List<PurchaseInvoice> findByOrganizationIdAndStatus(Long organizationId, PurchaseInvoice.PurchaseStatus status);
    List<PurchaseInvoice> findByOrganizationIdAndPurchaseDateBetween(Long organizationId, LocalDate startDate, LocalDate endDate);
    boolean existsByOrganizationIdAndStatusIn(Long organizationId, Collection<PurchaseInvoice.PurchaseStatus> statuses);

    @Query("""
            select case when count(i) > 0 then true else false end
            from PurchaseInvoice i
            where i.organization.id = :organizationId
              and i.status in :statuses
              and i.purchaseDate between :startDate and :endDate
            """)
    boolean existsOpenWorkInPeriod(
            @Param("organizationId") Long organizationId,
            @Param("statuses") Collection<PurchaseInvoice.PurchaseStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

