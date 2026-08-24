package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.*;
import com.justjava.ams.accountant.entity.*;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierStatementService {

    private final SupplierStatementRepository supplierStatementRepository;
    private final SupplierStatementLineRepository supplierStatementLineRepository;
    private final OrganizationRepository organizationRepository;
    private final VendorRepository vendorRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final GeneralLedgerRepository generalLedgerRepository;

    public SupplierStatementDTO createStatement(Long organizationId, SupplierStatementCreateRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        if (request == null || request.getVendorId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vendor is required");
        }
        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
        if (!vendor.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vendor does not belong to organization");
        }
        SupplierStatement statement = SupplierStatement.builder()
                .organization(organization)
                .vendor(vendor)
                .statementDate(requiredDate(request.getStatementDate(), "Statement date is required"))
                .startDate(requiredDate(request.getStartDate(), "Start date is required"))
                .endDate(requiredDate(request.getEndDate(), "End date is required"))
                .openingBalance(defaultAmount(request.getOpeningBalance()))
                .closingBalance(defaultAmount(request.getClosingBalance()))
                .status(SupplierStatement.StatementStatus.DRAFT)
                .build();
        SupplierStatement saved = supplierStatementRepository.save(statement);
        if (request.getLines() != null) {
            for (SupplierStatementLineDTO line : request.getLines()) {
                supplierStatementLineRepository.save(SupplierStatementLine.builder()
                        .supplierStatement(saved)
                        .transactionDate(requiredDate(line.getTransactionDate(), "Statement line date is required"))
                        .referenceNumber(required(line.getReferenceNumber(), "Statement line reference is required"))
                        .description(line.getDescription())
                        .debitAmount(defaultAmount(line.getDebitAmount()))
                        .creditAmount(defaultAmount(line.getCreditAmount()))
                        .status(SupplierStatementLine.MatchStatus.UNMATCHED)
                        .build());
            }
        }
        return getStatement(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<SupplierStatementDTO> getStatements(Long organizationId) {
        return supplierStatementRepository.findByOrganizationIdOrderByStatementDateDescIdDesc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SupplierStatementDTO getStatement(Long statementId) {
        return mapToDTO(findStatement(statementId));
    }

    public SupplierStatementDTO autoMatch(Long statementId) {
        SupplierStatement statement = findStatement(statementId);
        List<SupplierStatementLine> lines = supplierStatementLineRepository.findBySupplierStatementIdOrderByTransactionDateAscIdAsc(statementId);
        for (SupplierStatementLine line : lines) {
            if (!SupplierStatementLine.MatchStatus.UNMATCHED.equals(line.getStatus())) {
                continue;
            }
            purchaseInvoiceRepository
                    .findByOrganizationIdAndPurchaseOrderNumber(
                            statement.getOrganization().getId(),
                            line.getReferenceNumber())
                    .filter(invoice -> sameVendor(statement, invoice))
                    .filter(invoice -> invoice.getTotalAmount().compareTo(line.getDebitAmount()) == 0)
                    .ifPresent(invoice -> {
                        line.setMatchedPurchaseInvoice(invoice);
                        line.setStatus(SupplierStatementLine.MatchStatus.MATCHED);
                        supplierStatementLineRepository.save(line);
                    });
            if (SupplierStatementLine.MatchStatus.MATCHED.equals(line.getStatus())) {
                continue;
            }
            if (line.getCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
                generalLedgerRepository
                        .findByTransactionDateAndAmount(line.getTransactionDate(), line.getCreditAmount())
                        .stream()
                        .filter(gl -> GeneralLedger.SourceType.SUPPLIER_PAYMENT.equals(gl.getSourceType()))
                        .filter(gl -> gl.getAccount().getOrganization().getId().equals(statement.getOrganization().getId()))
                        .findFirst()
                        .ifPresent(gl -> {
                            line.setMatchedPayment(gl);
                            line.setStatus(SupplierStatementLine.MatchStatus.MATCHED);
                            supplierStatementLineRepository.save(line);
                        });
            }
        }
        statement.setStatus(SupplierStatement.StatementStatus.MATCHING);
        return mapToDTO(supplierStatementRepository.save(statement));
    }

    public SupplierStatementDTO markLineDisputed(Long lineId, String reason) {
        SupplierStatementLine line = supplierStatementLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier statement line not found"));
        line.setMatchedPurchaseInvoice(null);
        line.setMatchedPayment(null);
        line.setStatus(SupplierStatementLine.MatchStatus.DISPUTED);
        line.setDisputeReason(reason);
        supplierStatementLineRepository.save(line);
        return getStatement(line.getSupplierStatement().getId());
    }

    public SupplierStatementDTO completeStatement(Long statementId) {
        SupplierStatement statement = findStatement(statementId);
        boolean hasOpenLines = supplierStatementLineRepository.findBySupplierStatementIdOrderByTransactionDateAscIdAsc(statementId)
                .stream()
                .anyMatch(line -> SupplierStatementLine.MatchStatus.UNMATCHED.equals(line.getStatus()));
        if (hasOpenLines) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All statement lines must be matched or disputed before completion");
        }
        statement.setStatus(SupplierStatement.StatementStatus.COMPLETED);
        return mapToDTO(supplierStatementRepository.save(statement));
    }

    private boolean sameVendor(SupplierStatement statement, PurchaseInvoice invoice) {
        return invoice.getVendor() != null && invoice.getVendor().getId().equals(statement.getVendor().getId());
    }

    private SupplierStatement findStatement(Long statementId) {
        return supplierStatementRepository.findById(statementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier statement not found"));
    }

    private SupplierStatementDTO mapToDTO(SupplierStatement statement) {
        return SupplierStatementDTO.builder()
                .id(statement.getId())
                .organizationId(statement.getOrganization().getId())
                .vendorId(statement.getVendor().getId())
                .vendorName(statement.getVendor().getLegalName())
                .statementDate(statement.getStatementDate())
                .startDate(statement.getStartDate())
                .endDate(statement.getEndDate())
                .openingBalance(statement.getOpeningBalance())
                .closingBalance(statement.getClosingBalance())
                .status(statement.getStatus().name())
                .lines(supplierStatementLineRepository.findBySupplierStatementIdOrderByTransactionDateAscIdAsc(statement.getId())
                        .stream()
                        .map(this::mapLineToDTO)
                        .collect(Collectors.toList()))
                .createdAt(statement.getCreatedAt())
                .updatedAt(statement.getUpdatedAt())
                .build();
    }

    private SupplierStatementLineDTO mapLineToDTO(SupplierStatementLine line) {
        return SupplierStatementLineDTO.builder()
                .id(line.getId())
                .supplierStatementId(line.getSupplierStatement().getId())
                .transactionDate(line.getTransactionDate())
                .referenceNumber(line.getReferenceNumber())
                .description(line.getDescription())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .matchedPurchaseInvoiceId(line.getMatchedPurchaseInvoice() != null ? line.getMatchedPurchaseInvoice().getId() : null)
                .matchedPaymentId(line.getMatchedPayment() != null ? line.getMatchedPayment().getId() : null)
                .status(line.getStatus().name())
                .disputeReason(line.getDisputeReason())
                .createdAt(line.getCreatedAt())
                .updatedAt(line.getUpdatedAt())
                .build();
    }

    private LocalDate requiredDate(LocalDate value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
