package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.*;
import com.justjava.ams.accountant.entity.*;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentRunService {

    private final PaymentRunRepository paymentRunRepository;
    private final PaymentRunItemRepository paymentRunItemRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final BankAccountRepository bankAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final PurchaseInvoiceService purchaseInvoiceService;
    private final PaymentScheduleService paymentScheduleService;
    private final ApprovalWorkflowService approvalWorkflowService;

    public PaymentRunDTO createRun(Long organizationId, PaymentRunCreateRequest request, String createdBy) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        if (request == null || request.getBankAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account is required");
        }
        BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));
        if (!bankAccount.getOrganization().getId().equals(organizationId) || Boolean.FALSE.equals(bankAccount.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account does not belong to organization or is inactive");
        }
        LocalDate runDate = request.getRunDate() != null ? request.getRunDate() : LocalDate.now();
        LocalDate cutoffDate = request.getCutoffDate() != null ? request.getCutoffDate() : runDate;
        List<PaymentSchedule> schedules = paymentScheduleRepository
                .findByOrganizationIdAndStatusInAndScheduledPaymentDateLessThanEqualOrderByScheduledPaymentDateAscIdAsc(
                        organizationId,
                        List.of(PaymentSchedule.ScheduleStatus.APPROVED),
                        cutoffDate)
                .stream()
                .filter(schedule -> schedule.getAmountRemaining().compareTo(BigDecimal.ZERO) > 0)
                .filter(schedule -> !paymentRunItemRepository.existsByPaymentScheduleIdAndStatusIn(
                        schedule.getId(),
                        List.of(PaymentRunItem.PaymentRunItemStatus.PENDING)))
                .toList();
        if (schedules.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No approved payable schedules are eligible for this payment run");
        }
        PaymentRun run = PaymentRun.builder()
                .organization(organization)
                .bankAccount(bankAccount)
                .runDate(runDate)
                .cutoffDate(cutoffDate)
                .status(PaymentRun.PaymentRunStatus.DRAFT)
                .createdBy(defaultUser(createdBy))
                .build();
        PaymentRun savedRun = paymentRunRepository.save(run);
        for (PaymentSchedule schedule : schedules) {
            paymentRunItemRepository.save(PaymentRunItem.builder()
                    .paymentRun(savedRun)
                    .paymentSchedule(schedule)
                    .purchaseInvoice(schedule.getPurchaseInvoice())
                    .vendor(schedule.getVendor())
                    .amount(schedule.getAmountRemaining())
                    .status(PaymentRunItem.PaymentRunItemStatus.PENDING)
                    .build());
        }
        refreshRunTotals(savedRun);
        return getRun(savedRun.getId());
    }

    @Transactional(readOnly = true)
    public List<PaymentRunDTO> getRuns(Long organizationId) {
        return paymentRunRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentRunDTO getRun(Long runId) {
        PaymentRun run = findRun(runId);
        return mapToDTO(run);
    }

    public PaymentRunDTO submitRun(Long runId) {
        PaymentRun run = findRun(runId);
        if (!PaymentRun.PaymentRunStatus.DRAFT.equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT payment runs can be submitted");
        }
        if (paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(runId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment run has no items");
        }
        ApprovalDecisionDTO decision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                .organizationId(run.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.PAYMENTS)
                .transactionType("PAYMENT_RUN")
                .entityType("PaymentRun")
                .entityId(run.getId())
                .amount(run.getTotalAmount())
                .build());
        run.setStatus(Boolean.TRUE.equals(decision.getApprovalRequired())
                ? PaymentRun.PaymentRunStatus.READY_FOR_APPROVAL
                : PaymentRun.PaymentRunStatus.APPROVED);
        run.setApprovalRequestId(decision.getApprovalRequestId());
        run.setApprovalRuleId(decision.getApprovalRuleId());
        run.setApprovalRuleName(decision.getApprovalRuleName());
        run.setRequiredApprovals(decision.getRequiredApprovals());
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            run.setApprovedBy(defaultUser(null));
            run.setApprovedAt(LocalDateTime.now());
        }
        return mapToDTO(paymentRunRepository.save(run));
    }

    public PaymentRunDTO approveRun(Long runId, String approvedBy) {
        PaymentRun run = findRun(runId);
        if (!PaymentRun.PaymentRunStatus.READY_FOR_APPROVAL.equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only READY_FOR_APPROVAL payment runs can be approved");
        }
        if (run.getApprovalRequestId() != null) {
            approvalWorkflowService.approvePending("PaymentRun", run.getId(), "Approved by " + approvedBy);
        }
        run.setStatus(PaymentRun.PaymentRunStatus.APPROVED);
        run.setApprovedBy(defaultUser(approvedBy));
        run.setApprovedAt(LocalDateTime.now());
        return mapToDTO(paymentRunRepository.save(run));
    }

    public PaymentRunDTO rejectRun(Long runId, String rejectionReason) {
        PaymentRun run = findRun(runId);
        if (!PaymentRun.PaymentRunStatus.READY_FOR_APPROVAL.equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only READY_FOR_APPROVAL payment runs can be rejected");
        }
        if (run.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("PaymentRun", run.getId(), required(rejectionReason, "Rejection reason is required"));
        }
        run.setStatus(PaymentRun.PaymentRunStatus.REJECTED);
        return mapToDTO(paymentRunRepository.save(run));
    }

    public PaymentRunDTO executeRun(Long runId, String executedBy) {
        PaymentRun run = findRun(runId);
        if (!PaymentRun.PaymentRunStatus.APPROVED.equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED payment runs can be executed");
        }
        approvalWorkflowService.requireApproved("PaymentRun", runId);
        List<PaymentRunItem> items = paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(runId);
        int paid = 0;
        int failed = 0;
        for (PaymentRunItem item : items) {
            try {
                PaymentRequest request = PaymentRequest.builder()
                        .amount(item.getAmount())
                        .bankAccountId(run.getBankAccount().getId())
                        .paymentDate(run.getRunDate())
                        .paidBy(executedBy)
                        .notes("Payment run #" + run.getId())
                        .build();
                purchaseInvoiceService.recordPayment(item.getPurchaseInvoice().getId(), request, executedBy);
                item.setStatus(PaymentRunItem.PaymentRunItemStatus.PAID);
                item.setFailureReason(null);
                paymentRunItemRepository.save(item);
                paymentScheduleService.markPaid(item.getPaymentSchedule());
                paid++;
            } catch (Exception ex) {
                item.setStatus(PaymentRunItem.PaymentRunItemStatus.FAILED);
                item.setFailureReason(ex.getMessage());
                paymentRunItemRepository.save(item);
                failed++;
            }
        }
        run.setStatus(failed > 0 && paid > 0
                ? PaymentRun.PaymentRunStatus.PARTIALLY_EXECUTED
                : failed > 0 ? PaymentRun.PaymentRunStatus.CANCELLED : PaymentRun.PaymentRunStatus.EXECUTED);
        run.setExecutedBy(defaultUser(executedBy));
        run.setExecutedAt(LocalDateTime.now());
        return mapToDTO(paymentRunRepository.save(run));
    }

    public PaymentRunDTO cancelRun(Long runId) {
        PaymentRun run = findRun(runId);
        if (PaymentRun.PaymentRunStatus.EXECUTED.equals(run.getStatus())
                || PaymentRun.PaymentRunStatus.PARTIALLY_EXECUTED.equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Executed payment runs cannot be cancelled");
        }
        paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(runId).forEach(item -> {
            item.setStatus(PaymentRunItem.PaymentRunItemStatus.CANCELLED);
            paymentRunItemRepository.save(item);
        });
        run.setStatus(PaymentRun.PaymentRunStatus.CANCELLED);
        return mapToDTO(paymentRunRepository.save(run));
    }

    private void refreshRunTotals(PaymentRun run) {
        List<PaymentRunItem> items = paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(run.getId());
        run.setItemCount(items.size());
        run.setTotalAmount(items.stream().map(PaymentRunItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        paymentRunRepository.save(run);
    }

    private PaymentRun findRun(Long runId) {
        return paymentRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment run not found"));
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

    private PaymentRunDTO mapToDTO(PaymentRun run) {
        List<PaymentRunItemDTO> items = paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(run.getId())
                .stream()
                .map(this::mapItemToDTO)
                .collect(Collectors.toList());
        return PaymentRunDTO.builder()
                .id(run.getId())
                .organizationId(run.getOrganization().getId())
                .bankAccountId(run.getBankAccount().getId())
                .bankName(run.getBankAccount().getBankName())
                .runDate(run.getRunDate())
                .cutoffDate(run.getCutoffDate())
                .totalAmount(run.getTotalAmount())
                .itemCount(run.getItemCount())
                .status(run.getStatus().name())
                .createdBy(run.getCreatedBy())
                .approvedBy(run.getApprovedBy())
                .approvalRequestId(run.getApprovalRequestId())
                .approvalRuleId(run.getApprovalRuleId())
                .approvalRuleName(run.getApprovalRuleName())
                .requiredApprovals(run.getRequiredApprovals())
                .approvedAt(run.getApprovedAt())
                .executedBy(run.getExecutedBy())
                .executedAt(run.getExecutedAt())
                .items(items)
                .createdAt(run.getCreatedAt())
                .updatedAt(run.getUpdatedAt())
                .build();
    }

    private PaymentRunItemDTO mapItemToDTO(PaymentRunItem item) {
        PurchaseInvoice invoice = item.getPurchaseInvoice();
        Vendor vendor = item.getVendor();
        return PaymentRunItemDTO.builder()
                .id(item.getId())
                .paymentRunId(item.getPaymentRun().getId())
                .paymentScheduleId(item.getPaymentSchedule().getId())
                .purchaseInvoiceId(invoice.getId())
                .vendorId(vendor != null ? vendor.getId() : null)
                .vendorName(vendor != null ? vendor.getLegalName() : invoice.getVendorName())
                .purchaseOrderNumber(invoice.getPurchaseOrderNumber())
                .amount(item.getAmount())
                .status(item.getStatus().name())
                .failureReason(item.getFailureReason())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
