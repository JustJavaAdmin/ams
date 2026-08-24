package com.justjava.ams.accountant.service;

import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.dto.FiscalPeriodDTO;
import com.justjava.ams.accountant.entity.FiscalPeriod;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.accountant.entity.PurchaseInvoice;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.accountant.repository.FiscalPeriodRepository;
import com.justjava.ams.accountant.repository.ManualJournalRepository;
import com.justjava.ams.accountant.repository.PurchaseInvoiceRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FiscalPeriodService {
    private static final String ENTITY_TYPE = "FiscalPeriod";

    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final ManualJournalRepository manualJournalRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;

    public FiscalPeriodDTO createFiscalPeriod(Long organizationId, FiscalPeriodDTO dto) {
        Organization organization = findOrganization(organizationId);

        if (dto.getYear() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year is required");
        }
        if (dto.getQuarter() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quarter is required");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and end date are required");
        }
        if (!dto.getStartDate().isBefore(dto.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        if (fiscalPeriodRepository.findByOrganizationIdAndYearAndQuarter(organizationId, dto.getYear(), dto.getQuarter()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fiscal period already exists for this year and quarter");
        }
        if (!fiscalPeriodRepository.findOverlappingPeriods(organizationId, dto.getStartDate(), dto.getEndDate()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fiscal period overlaps an existing period");
        }

        FiscalPeriod period = FiscalPeriod.builder()
                .organization(organization)
                .year(dto.getYear())
                .quarter(dto.getQuarter())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(FiscalPeriod.PeriodStatus.OPEN)
                .closed(false)
                .build();

        FiscalPeriod saved = fiscalPeriodRepository.save(period);
        FiscalPeriodDTO savedDto = mapToDTO(saved);
        log(organizationId, saved.getId(), "CREATE", null, savedDto.toString(), "Created fiscal period " + saved.getYear() + " Q" + saved.getQuarter());
        return savedDto;
    }

    @Transactional(readOnly = true)
    public FiscalPeriodDTO getFiscalPeriod(Long periodId) {
        return mapToDTO(findFiscalPeriod(periodId));
    }

    @Transactional(readOnly = true)
    public List<FiscalPeriodDTO> getFiscalPeriodsByOrganization(Long organizationId) {
        findOrganization(organizationId);

        return fiscalPeriodRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public FiscalPeriodDTO getFiscalPeriodById(Long periodId) {
        return getFiscalPeriod(periodId);
    }

    @Transactional(readOnly = true)
    public FiscalPeriodDTO getCurrentFiscalPeriod(Long organizationId) {
        LocalDateTime now = LocalDateTime.now();
        return fiscalPeriodRepository.findByOrganizationIdAndStatusContainingDate(
                        organizationId,
                        FiscalPeriod.PeriodStatus.OPEN,
                        now)
                .map(this::mapToDTO)
                .orElse(null);
    }

    public FiscalPeriodDTO lockFiscalPeriod(Long periodId) {
        FiscalPeriod period = findFiscalPeriod(periodId);
        FiscalPeriodDTO oldValue = mapToDTO(period);
        requireNoOpenAccountingWork(period);

        period.setStatus(FiscalPeriod.PeriodStatus.LOCKED);
        period.setClosed(true);
        period.setClosedDate(LocalDateTime.now());

        FiscalPeriod saved = fiscalPeriodRepository.save(period);
        FiscalPeriodDTO savedDto = mapToDTO(saved);
        log(saved.getOrganization().getId(), saved.getId(), "UPDATE", oldValue.toString(), savedDto.toString(), "Locked fiscal period " + saved.getYear() + " Q" + saved.getQuarter());
        return savedDto;
    }

    @Transactional(readOnly = true)
    public List<FiscalPeriodDTO> getPeriodsByYear(Long organizationId, Integer year) {
        findOrganization(organizationId);

        return fiscalPeriodRepository.findByOrganizationIdAndYearOrderByQuarterAsc(organizationId, year)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public FiscalPeriodDTO closeFiscalPeriod(Long periodId) {
        FiscalPeriod period = findFiscalPeriod(periodId);
        FiscalPeriodDTO oldValue = mapToDTO(period);
        requireNoOpenAccountingWork(period);

        period.setStatus(FiscalPeriod.PeriodStatus.CLOSED);
        period.setClosed(true);
        period.setClosedDate(LocalDateTime.now());

        FiscalPeriod saved = fiscalPeriodRepository.save(period);
        FiscalPeriodDTO savedDto = mapToDTO(saved);
        log(saved.getOrganization().getId(), saved.getId(), "UPDATE", oldValue.toString(), savedDto.toString(), "Closed fiscal period " + saved.getYear() + " Q" + saved.getQuarter());
        return savedDto;
    }

    @Transactional(readOnly = true)
    public boolean isDateInOpenPeriod(Long organizationId, LocalDate journalDate) {
        if (journalDate == null) {
            return false;
        }

        return fiscalPeriodRepository.findByOrganizationIdAndStatusContainingDate(
                        organizationId,
                        FiscalPeriod.PeriodStatus.OPEN,
                        journalDate.atStartOfDay())
                .isPresent();
    }

    @Transactional(readOnly = true)
    public FiscalPeriodDTO requireOpenPeriod(Long organizationId, LocalDate journalDate) {
        if (journalDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal date is required");
        }

        FiscalPeriod period = fiscalPeriodRepository.findByOrganizationIdAndStatusContainingDate(
                        organizationId,
                        FiscalPeriod.PeriodStatus.OPEN,
                        journalDate.atStartOfDay())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Journal date is not in an open fiscal period"));

        return mapToDTO(period);
    }

    private FiscalPeriodDTO mapToDTO(FiscalPeriod period) {
        return FiscalPeriodDTO.builder()
                .id(period.getId())
                .organizationId(period.getOrganization().getId())
                .year(period.getYear())
                .quarter(period.getQuarter())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus().toString())
                .closed(period.getClosed())
                .closedDate(period.getClosedDate())
                .createdAt(period.getCreatedAt())
                .updatedAt(period.getUpdatedAt())
                .build();
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private FiscalPeriod findFiscalPeriod(Long periodId) {
        return fiscalPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiscal period not found"));
    }

    public FiscalPeriodDTO createFiscalPeriod(Long organizationId, Integer year, Integer quarter, LocalDate startDate, LocalDate endDate) {
        FiscalPeriodDTO dto = FiscalPeriodDTO.builder()
                .year(year)
                .quarter(quarter)
                .startDate(startDate.atStartOfDay())
                .endDate(endDate.atTime(LocalTime.MAX))
                .build();

        return createFiscalPeriod(organizationId, dto);
    }

    private void log(Long organizationId, Long entityId, String action, String oldValue, String newValue, String description) {
        AuditLogDTO auditLog = AuditLogDTO.builder()
                .entityType(ENTITY_TYPE)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .description(description)
                .build();

        auditLogService.createAuditLog(organizationId, auditLog);
    }

    private void requireNoOpenAccountingWork(FiscalPeriod period) {
        Long organizationId = period.getOrganization().getId();
        LocalDate startDate = period.getStartDate().toLocalDate();
        LocalDate endDate = period.getEndDate().toLocalDate();
        if (manualJournalRepository.existsOpenWorkInPeriod(
                organizationId,
                List.of(ManualJournal.JournalStatus.DRAFT, ManualJournal.JournalStatus.SUBMITTED, ManualJournal.JournalStatus.APPROVED),
                startDate,
                endDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot close fiscal period while manual journals are unposted");
        }
        if (customerInvoiceRepository.existsOpenWorkInPeriod(
                organizationId,
                List.of(CustomerInvoice.InvoiceStatus.DRAFT, CustomerInvoice.InvoiceStatus.APPROVED, CustomerInvoice.InvoiceStatus.SENT),
                startDate,
                endDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot close fiscal period while customer invoices are unposted");
        }
        if (purchaseInvoiceRepository.existsOpenWorkInPeriod(
                organizationId,
                List.of(PurchaseInvoice.PurchaseStatus.DRAFT, PurchaseInvoice.PurchaseStatus.SUBMITTED, PurchaseInvoice.PurchaseStatus.APPROVED),
                startDate,
                endDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot close fiscal period while purchase invoices are unposted");
        }
    }
}

