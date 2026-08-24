package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, Long> {
    List<PaymentSchedule> findByOrganizationIdOrderByScheduledPaymentDateAscIdAsc(Long organizationId);
    List<PaymentSchedule> findByOrganizationIdAndStatusOrderByScheduledPaymentDateAscIdAsc(Long organizationId, PaymentSchedule.ScheduleStatus status);
    List<PaymentSchedule> findByOrganizationIdAndStatusInAndScheduledPaymentDateBetweenOrderByScheduledPaymentDateAscIdAsc(
            Long organizationId,
            Collection<PaymentSchedule.ScheduleStatus> statuses,
            LocalDate fromDate,
            LocalDate toDate);
    List<PaymentSchedule> findByOrganizationIdAndStatusInAndScheduledPaymentDateLessThanEqualOrderByScheduledPaymentDateAscIdAsc(
            Long organizationId,
            Collection<PaymentSchedule.ScheduleStatus> statuses,
            LocalDate cutoffDate);
    List<PaymentSchedule> findByPurchaseInvoiceIdAndStatusIn(Long purchaseInvoiceId, Collection<PaymentSchedule.ScheduleStatus> statuses);
}
