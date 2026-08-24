package com.justjava.ams.accountant.service;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.financeAdmin.entity.TaxJurisdiction;
import com.justjava.ams.financeAdmin.repository.TaxJurisdictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

    @Mock
    private TaxJurisdictionRepository taxJurisdictionRepository;

    @InjectMocks
    private TaxCalculationService taxCalculationService;

    @Test
    void calculatesPercentageTaxFromConfiguredJurisdiction() {
        TaxJurisdiction vat = taxJurisdiction(1L, true, TaxJurisdiction.TaxCalculationType.PERCENTAGE, "7.50");
        when(taxJurisdictionRepository.findById(10L)).thenReturn(Optional.of(vat));

        TaxCalculationService.Result result = taxCalculationService.calculate(1L, 10L, new BigDecimal("2600000.00"));

        assertThat(result.subtotal()).isEqualByComparingTo("2600000.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("195000.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("2795000.00");
        assertThat(result.taxCode()).isEqualTo("NG-VAT");
        assertThat(result.taxRate()).isEqualByComparingTo("7.50");
    }

    @Test
    void returnsZeroTaxWhenNoJurisdictionIsSelected() {
        TaxCalculationService.Result result = taxCalculationService.calculate(1L, null, new BigDecimal("100.00"));

        assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.jurisdiction()).isNull();
    }

    @Test
    void rejectsInactiveJurisdiction() {
        TaxJurisdiction inactive = taxJurisdiction(1L, false, TaxJurisdiction.TaxCalculationType.PERCENTAGE, "7.50");
        when(taxJurisdictionRepository.findById(10L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> taxCalculationService.calculate(1L, 10L, new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void rejectsUnsupportedCalculationType() {
        TaxJurisdiction fixedAmount = taxJurisdiction(1L, true, TaxJurisdiction.TaxCalculationType.FIXED_AMOUNT, "7.50");
        when(taxJurisdictionRepository.findById(10L)).thenReturn(Optional.of(fixedAmount));

        assertThatThrownBy(() -> taxCalculationService.calculate(1L, 10L, new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only percentage");
    }

    private TaxJurisdiction taxJurisdiction(Long organizationId, boolean active, TaxJurisdiction.TaxCalculationType calculationType, String rate) {
        return TaxJurisdiction.builder()
                .id(10L)
                .organization(Organization.builder().id(organizationId).build())
                .jurisdictionCode("NG-VAT")
                .jurisdictionName("Nigeria VAT")
                .taxType(TaxJurisdiction.TaxType.VAT)
                .calculationType(calculationType)
                .taxRate(new BigDecimal(rate))
                .active(active)
                .build();
    }
}
