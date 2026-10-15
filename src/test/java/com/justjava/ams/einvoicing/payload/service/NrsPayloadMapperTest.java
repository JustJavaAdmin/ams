package com.justjava.ams.einvoicing.payload.service;

import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.InvoiceLineItemDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NrsPayloadMapperTest {

    @Mock
    private CustomerInvoiceService customerInvoiceService;

    private NrsPayloadMapper nrsPayloadMapper;

    @BeforeEach
    void setUp() {
        nrsPayloadMapper = new NrsPayloadMapper(customerInvoiceService);
    }

    @Test
    void mapsStandardVatInvoiceToNrsPayload() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("NG-VAT", "7.50", "7.50"));

        var payload = nrsPayloadMapper.mapCustomerInvoice(10L);

        assertThat(payload.getInvoiceHeader().getSupplierTaxID()).isEqualTo("NG-12345678-0001");
        assertThat(payload.getInvoiceHeader().getBuyerTaxID()).isEqualTo("NG-87654321-0002");
        assertThat(payload.getInvoiceHeader().getInvoiceTypeCode()).isEqualTo("380");
        assertThat(payload.getTaxTotal().getTaxCategoryCode()).isEqualTo("S");
        assertThat(payload.getTaxTotal().getTaxPercentage()).isEqualByComparingTo("7.50");
        assertThat(payload.getTaxTotal().getTaxAmount()).isEqualByComparingTo("7.50");
        assertThat(payload.getInvoiceLines()).singleElement().satisfies(line -> {
            assertThat(line.getNrsUnitOfMeasurement()).isEqualTo("EA");
            assertThat(line.getVatAmount()).isEqualByComparingTo("7.50");
        });
    }

    @Test
    void rejectsStandardVatInvoiceWithWrongVatRate() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("NG-VAT", "5.00", "5.00"));

        assertThatThrownBy(() -> nrsPayloadMapper.mapCustomerInvoice(10L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsZeroTaxWithoutExemptionOrZeroRatedCode() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("NG-VAT", "0.00", "0.00"));

        assertThatThrownBy(() -> nrsPayloadMapper.mapCustomerInvoice(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Zero VAT");
    }

    @Test
    void allowsZeroRatedCode() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("ZERO-RATED", "0.00", "0.00"));

        assertThat(nrsPayloadMapper.mapCustomerInvoice(10L).getTaxTotal().getTaxCategoryCode())
                .isEqualTo("Z");
    }

    @Test
    void requiresSupplierAndBuyerTaxIds() {
        CustomerInvoiceDTO invoice = invoice("NG-VAT", "7.50", "7.50");
        invoice.setSupplierTaxId(null);
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice);

        assertThatThrownBy(() -> nrsPayloadMapper.mapCustomerInvoice(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Supplier Tax ID");
    }

    private CustomerInvoiceDTO invoice(String taxCode, String taxRate, String taxAmount) {
        return CustomerInvoiceDTO.builder()
                .id(10L)
                .organizationId(1L)
                .invoiceNumber("INV-10")
                .transactionClassification("B2B")
                .supplierTaxId("NG-12345678-0001")
                .buyerTaxId("NG-87654321-0002")
                .invoiceDate(LocalDate.of(2026, 9, 16))
                .subtotal(new BigDecimal("100.00"))
                .taxCode(taxCode)
                .taxRate(new BigDecimal(taxRate))
                .taxAmount(new BigDecimal(taxAmount))
                .totalAmount(new BigDecimal("100.00").add(new BigDecimal(taxAmount)))
                .lineItems(Set.of(InvoiceLineItemDTO.builder()
                        .id(1L)
                        .description("Consulting")
                        .quantity(new BigDecimal("1.00"))
                        .unitPrice(new BigDecimal("100.00"))
                        .lineTotal(new BigDecimal("100.00"))
                        .build()))
                .build();
    }
}
