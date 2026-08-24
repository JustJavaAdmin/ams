package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.*;
import com.justjava.ams.accountant.entity.PaymentSchedule;
import com.justjava.ams.accountant.entity.PurchaseInvoice;
import com.justjava.ams.accountant.repository.PaymentScheduleRepository;
import com.justjava.ams.accountant.repository.PurchaseInvoiceRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.service.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentScheduleService {

    private static final List<PaymentSchedule.ScheduleStatus> ACTIVE_STATUSES = List.of(
            PaymentSchedule.ScheduleStatus.PLANNED,
            PaymentSchedule.ScheduleStatus.READY_FOR_APPROVAL,
            PaymentSchedule.ScheduleStatus.APPROVED,
            PaymentSchedule.ScheduleStatus.HELD);

    private final PaymentScheduleRepository paymentScheduleRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final ApprovalWorkflowService approvalWorkflowService;

    public PaymentScheduleDTO createDefaultScheduleForInvoice(Long invoiceId, String createdBy) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        BigDecimal outstanding = outstanding(invoice);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        List<PaymentSchedule> existing = paymentScheduleRepository.findByPurchaseInvoiceIdAndStatusIn(invoiceId, ACTIVE_STATUSES);
        if (!existing.isEmpty()) {
            return mapToDTO(existing.get(0));
        }
        PaymentSchedule schedule = PaymentSchedule.builder()
                .organization(invoice.getOrganization())
                .purchaseInvoice(invoice)
                .vendor(invoice.getVendor())
                .dueDate(invoice.getDueDate())
                .scheduledPaymentDate(invoice.getDueDate())
                .amountScheduled(outstanding)
                .amountRemaining(outstanding)
                .priority(5)
                .status(PaymentSchedule.ScheduleStatus.PLANNED)
                .createdBy(defaultUser(createdBy))
                .build();
        return mapToDTO(paymentScheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<PaymentScheduleDTO> getSchedules(Long organizationId, LocalDate fromDate, LocalDate toDate, String status) {
        List<PaymentSchedule> schedules;
        if (status != null && !status.isBlank()) {
            schedules = paymentScheduleRepository.findByOrganizationIdAndStatusOrderByScheduledPaymentDateAscIdAsc(
                    organizationId,
                    parseStatus(status));
        } else if (fromDate != null && toDate != null) {
            schedules = paymentScheduleRepository.findByOrganizationIdAndStatusInAndScheduledPaymentDateBetweenOrderByScheduledPaymentDateAscIdAsc(
                    organizationId,
                    ACTIVE_STATUSES,
                    fromDate,
                    toDate);
        } else {
            schedules = paymentScheduleRepository.findByOrganizationIdOrderByScheduledPaymentDateAscIdAsc(organizationId);
        }
        return schedules.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentScheduleDTO> getDueSchedules(Long organizationId, LocalDate asOfDate) {
        LocalDate cutoff = asOfDate != null ? asOfDate : LocalDate.now();
        return paymentScheduleRepository
                .findByOrganizationIdAndStatusInAndScheduledPaymentDateLessThanEqualOrderByScheduledPaymentDateAscIdAsc(
                        organizationId,
                        List.of(PaymentSchedule.ScheduleStatus.PLANNED, PaymentSchedule.ScheduleStatus.APPROVED),
                        cutoff)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public PaymentScheduleDTO approveSchedule(Long scheduleId, String approvedBy) {
        PaymentSchedule schedule = findSchedule(scheduleId);
        if (!PaymentSchedule.ScheduleStatus.PLANNED.equals(schedule.getStatus())) {
            if (!PaymentSchedule.ScheduleStatus.READY_FOR_APPROVAL.equals(schedule.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only READY_FOR_APPROVAL payment schedules can be approved");
            }
            if (schedule.getApprovalRequestId() != null) {
                approvalWorkflowService.approvePending("PaymentSchedule", schedule.getId(), "Approved by " + approvedBy);
            }
            schedule.setStatus(PaymentSchedule.ScheduleStatus.APPROVED);
            schedule.setApprovedBy(defaultUser(approvedBy));
            schedule.setApprovedAt(java.time.LocalDateTime.now());
            return mapToDTO(paymentScheduleRepository.save(schedule));
        }
        return submitSchedule(scheduleId);
    }

    public PaymentScheduleDTO submitSchedule(Long scheduleId) {
        PaymentSchedule schedule = findSchedule(scheduleId);
        if (!PaymentSchedule.ScheduleStatus.PLANNED.equals(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PLANNED payment schedules can be submitted");
        }
        ApprovalDecisionDTO decision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                .organizationId(schedule.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.PAYMENTS)
                .transactionType("PAYMENT_SCHEDULE")
                .entityType("PaymentSchedule")
                .entityId(schedule.getId())
                .amount(schedule.getAmountScheduled())
                .build());
        schedule.setApprovalRequestId(decision.getApprovalRequestId());
        schedule.setApprovalRuleId(decision.getApprovalRuleId());
        schedule.setApprovalRuleName(decision.getApprovalRuleName());
        schedule.setRequiredApprovals(decision.getRequiredApprovals());
        schedule.setStatus(Boolean.TRUE.equals(decision.getApprovalRequired())
                ? PaymentSchedule.ScheduleStatus.READY_FOR_APPROVAL
                : PaymentSchedule.ScheduleStatus.APPROVED);
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            schedule.setApprovedBy(defaultUser(null));
            schedule.setApprovedAt(java.time.LocalDateTime.now());
        }
        return mapToDTO(paymentScheduleRepository.save(schedule));
    }

    public PaymentScheduleDTO rejectSchedule(Long scheduleId, String rejectionReason) {
        PaymentSchedule schedule = findSchedule(scheduleId);
        if (!PaymentSchedule.ScheduleStatus.READY_FOR_APPROVAL.equals(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only READY_FOR_APPROVAL payment schedules can be rejected");
        }
        if (schedule.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("PaymentSchedule", schedule.getId(), required(rejectionReason, "Rejection reason is required"));
        }
        schedule.setStatus(PaymentSchedule.ScheduleStatus.REJECTED);
        return mapToDTO(paymentScheduleRepository.save(schedule));
    }

    public PaymentScheduleDTO holdSchedule(Long scheduleId, PaymentScheduleRequest request) {
        PaymentSchedule schedule = findSchedule(scheduleId);
        if (PaymentSchedule.ScheduleStatus.PAID.equals(schedule.getStatus())
                || PaymentSchedule.ScheduleStatus.CANCELLED.equals(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid or cancelled schedules cannot be held");
        }
        schedule.setStatus(PaymentSchedule.ScheduleStatus.HELD);
        schedule.setHoldReason(request != null ? request.getReason() : null);
        return mapToDTO(paymentScheduleRepository.save(schedule));
    }

    public PaymentScheduleDTO reschedule(Long scheduleId, PaymentScheduleRequest request) {
        PaymentSchedule schedule = findSchedule(scheduleId);
        if (PaymentSchedule.ScheduleStatus.PAID.equals(schedule.getStatus())
                || PaymentSchedule.ScheduleStatus.CANCELLED.equals(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid or cancelled schedules cannot be rescheduled");
        }
        if (request == null || request.getScheduledPaymentDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled payment date is required");
        }
        if (request.getAmountScheduled() != null) {
            BigDecimal maxAllowed = outstanding(schedule.getPurchaseInvoice()).add(schedule.getAmountRemaining());
            if (request.getAmountScheduled().compareTo(BigDecimal.ZERO) <= 0
                    || request.getAmountScheduled().compareTo(maxAllowed) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled amount must be positive and not exceed invoice balance");
            }
            schedule.setAmountScheduled(request.getAmountScheduled());
            schedule.setAmountRemaining(request.getAmountScheduled());
        }
        if (request.getPriority() != null) {
            schedule.setPriority(request.getPriority());
        }
        schedule.setScheduledPaymentDate(request.getScheduledPaymentDate());
        if (PaymentSchedule.ScheduleStatus.HELD.equals(schedule.getStatus())) {
            schedule.setStatus(PaymentSchedule.ScheduleStatus.PLANNED);
            schedule.setHoldReason(null);
        }
        return mapToDTO(paymentScheduleRepository.save(schedule));
    }

    public PaymentScheduleDTO cancelSchedule(Long scheduleId, String reason) {
        PaymentSchedule schedule = findSchedule(scheduleId);
        if (PaymentSchedule.ScheduleStatus.PAID.equals(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid schedules cannot be cancelled");
        }
        schedule.setStatus(PaymentSchedule.ScheduleStatus.CANCELLED);
        schedule.setHoldReason(reason);
        return mapToDTO(paymentScheduleRepository.save(schedule));
    }

    public void markPaid(PaymentSchedule schedule) {
        schedule.setAmountRemaining(BigDecimal.ZERO);
        schedule.setStatus(PaymentSchedule.ScheduleStatus.PAID);
        paymentScheduleRepository.save(schedule);
    }

    public void applyInvoicePayment(PurchaseInvoice invoice, BigDecimal paymentAmount) {
        BigDecimal remainingPayment = paymentAmount != null ? paymentAmount : BigDecimal.ZERO;
        if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<PaymentSchedule> schedules = paymentScheduleRepository.findByPurchaseInvoiceIdAndStatusIn(
                invoice.getId(),
                List.of(PaymentSchedule.ScheduleStatus.PLANNED, PaymentSchedule.ScheduleStatus.APPROVED, PaymentSchedule.ScheduleStatus.HELD));
        for (PaymentSchedule schedule : schedules) {
            if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal scheduleRemaining = schedule.getAmountRemaining() != null ? schedule.getAmountRemaining() : BigDecimal.ZERO;
            BigDecimal applied = remainingPayment.min(scheduleRemaining);
            schedule.setAmountRemaining(scheduleRemaining.subtract(applied));
            if (schedule.getAmountRemaining().compareTo(BigDecimal.ZERO) <= 0) {
                schedule.setAmountRemaining(BigDecimal.ZERO);
                schedule.setStatus(PaymentSchedule.ScheduleStatus.PAID);
            }
            paymentScheduleRepository.save(schedule);
            remainingPayment = remainingPayment.subtract(applied);
        }
    }

    @Transactional(readOnly = true)
    public CashRequirementForecastResponse forecast(Long organizationId, LocalDate fromDate, LocalDate toDate, String bucket) {
        LocalDate from = fromDate != null ? fromDate : LocalDate.now();
        LocalDate to = toDate != null ? toDate : from.plusDays(28);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
        }
        String normalizedBucket = bucket != null && bucket.equalsIgnoreCase("DAILY") ? "DAILY" : "WEEKLY";
        List<PaymentSchedule> schedules = paymentScheduleRepository
                .findByOrganizationIdAndStatusInAndScheduledPaymentDateBetweenOrderByScheduledPaymentDateAscIdAsc(
                        organizationId,
                        List.of(PaymentSchedule.ScheduleStatus.PLANNED, PaymentSchedule.ScheduleStatus.APPROVED),
                        from,
                        to);
        BigDecimal total = schedules.stream().map(PaymentSchedule::getAmountRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = paymentScheduleRepository
                .findByOrganizationIdAndStatusInAndScheduledPaymentDateLessThanEqualOrderByScheduledPaymentDateAscIdAsc(
                        organizationId,
                        List.of(PaymentSchedule.ScheduleStatus.PLANNED, PaymentSchedule.ScheduleStatus.APPROVED),
                        from.minusDays(1))
                .stream()
                .map(PaymentSchedule::getAmountRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate dueSoonEnd = LocalDate.now().plusDays(7);
        BigDecimal dueSoon = schedules.stream()
                .filter(schedule -> !schedule.getScheduledPaymentDate().isAfter(dueSoonEnd))
                .map(PaymentSchedule::getAmountRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CashRequirementForecastRowDTO> rows = bucketRows(schedules, from, to, normalizedBucket);
        return CashRequirementForecastResponse.builder()
                .organizationId(organizationId)
                .fromDate(from)
                .toDate(to)
                .bucket(normalizedBucket)
                .totalScheduled(total)
                .overdueAmount(overdue)
                .dueSoonAmount(dueSoon)
                .rows(rows)
                .build();
    }

    private List<CashRequirementForecastRowDTO> bucketRows(List<PaymentSchedule> schedules, LocalDate from, LocalDate to, String bucket) {
        List<CashRequirementForecastRowDTO> rows = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate end = "DAILY".equals(bucket) ? cursor : cursor.plusDays(6);
            if (end.isAfter(to)) {
                end = to;
            }
            LocalDate bucketStart = cursor;
            LocalDate bucketEnd = end;
            List<PaymentSchedule> bucketSchedules = schedules.stream()
                    .filter(schedule -> !schedule.getScheduledPaymentDate().isBefore(bucketStart)
                            && !schedule.getScheduledPaymentDate().isAfter(bucketEnd))
                    .toList();
            rows.add(CashRequirementForecastRowDTO.builder()
                    .bucketStart(bucketStart)
                    .bucketEnd(bucketEnd)
                    .scheduledAmount(bucketSchedules.stream().map(PaymentSchedule::getAmountRemaining).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .scheduleCount(bucketSchedules.size())
                    .build());
            cursor = end.plusDays(1);
        }
        return rows;
    }

    private PaymentSchedule findSchedule(Long scheduleId) {
        return paymentScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment schedule not found"));
    }

    private PaymentSchedule.ScheduleStatus parseStatus(String status) {
        try {
            return PaymentSchedule.ScheduleStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment schedule status");
        }
    }

    private BigDecimal outstanding(PurchaseInvoice invoice) {
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        return total.subtract(paid);
    }

    private String defaultUser(String user) {
        return user != null && !user.isBlank() ? user : "system";
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    public PaymentScheduleDTO mapToDTO(PaymentSchedule schedule) {
        PurchaseInvoice invoice = schedule.getPurchaseInvoice();
        return PaymentScheduleDTO.builder()
                .id(schedule.getId())
                .organizationId(schedule.getOrganization().getId())
                .purchaseInvoiceId(invoice.getId())
                .vendorId(schedule.getVendor() != null ? schedule.getVendor().getId() : null)
                .vendorName(schedule.getVendor() != null ? schedule.getVendor().getLegalName() : invoice.getVendorName())
                .purchaseOrderNumber(invoice.getPurchaseOrderNumber())
                .dueDate(schedule.getDueDate())
                .scheduledPaymentDate(schedule.getScheduledPaymentDate())
                .amountScheduled(schedule.getAmountScheduled())
                .amountRemaining(schedule.getAmountRemaining())
                .priority(schedule.getPriority())
                .status(schedule.getStatus().name())
                .holdReason(schedule.getHoldReason())
                .createdBy(schedule.getCreatedBy())
                .approvedBy(schedule.getApprovedBy())
                .approvalRequestId(schedule.getApprovalRequestId())
                .approvalRuleId(schedule.getApprovalRuleId())
                .approvalRuleName(schedule.getApprovalRuleName())
                .requiredApprovals(schedule.getRequiredApprovals())
                .approvedAt(schedule.getApprovedAt())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
