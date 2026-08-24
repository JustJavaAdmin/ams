package com.justjava.ams.accountant.service;

import com.justjava.ams.financeAdmin.entity.TaxJurisdiction;
import com.justjava.ams.financeAdmin.repository.TaxJurisdictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TaxCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final TaxJurisdictionRepository taxJurisdictionRepository;

    public Result calculate(Long organizationId, Long taxJurisdictionId, BigDecimal subtotal) {
        BigDecimal normalizedSubtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        if (normalizedSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subtotal cannot be negative");
        }

        if (taxJurisdictionId == null) {
            return Result.noTax(normalizedSubtotal);
        }

        TaxJurisdiction jurisdiction = taxJurisdictionRepository.findById(taxJurisdictionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax jurisdiction not found"));
        if (!jurisdiction.getOrganization().getId().equals(organizationId) || Boolean.FALSE.equals(jurisdiction.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax jurisdiction does not belong to organization or is inactive");
        }
        if (!TaxJurisdiction.TaxCalculationType.PERCENTAGE.equals(jurisdiction.getCalculationType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only percentage tax calculation is currently supported");
        }
        if (jurisdiction.getTaxRate() == null || jurisdiction.getTaxRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax rate cannot be negative");
        }

        BigDecimal taxAmount = normalizedSubtotal
                .multiply(jurisdiction.getTaxRate())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        return new Result(
                normalizedSubtotal,
                taxAmount,
                normalizedSubtotal.add(taxAmount),
                jurisdiction,
                jurisdiction.getJurisdictionCode(),
                jurisdiction.getTaxRate(),
                jurisdiction.getCalculationType().name());
    }

    public record Result(
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            TaxJurisdiction jurisdiction,
            String taxCode,
            BigDecimal taxRate,
            String taxCalculationType) {
        private static Result noTax(BigDecimal subtotal) {
            return new Result(subtotal, BigDecimal.ZERO, subtotal, null, null, BigDecimal.ZERO, null);
        }
    }
}
