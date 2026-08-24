package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.entity.Customer;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.common.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerInvoiceCollectionControlTest {

    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;

    @Mock
    private InvoiceLineItemRepository invoiceLineItemRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private GeneralLedgerService generalLedgerService;

    @Mock
    private FiscalPeriodService fiscalPeriodService;

    @Mock
    private TaxCalculationService taxCalculationService;

    @Mock
    private ReceivablesCollectionService receivablesCollectionService;

    @InjectMocks
    private CustomerInvoiceService customerInvoiceService;

    @Test
    void creditHoldBlocksSendingInvoice() {
        Organization organization = Organization.builder().id(1L).build();
        Customer customer = Customer.builder()
                .id(2L)
                .organization(organization)
                .legalName("Acme Plc")
                .creditHold(true)
                .creditHoldReason("Broken promise")
                .build();
        CustomerInvoice invoice = CustomerInvoice.builder()
                .id(3L)
                .organization(organization)
                .customer(customer)
                .invoiceNumber("AR-3")
                .invoiceDate(LocalDate.of(2026, 1, 1))
                .dueDate(LocalDate.of(2026, 1, 31))
                .totalAmount(new BigDecimal("1000.00"))
                .amountPaid(BigDecimal.ZERO)
                .status(CustomerInvoice.InvoiceStatus.DRAFT)
                .build();
        when(customerInvoiceRepository.findById(3L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> customerInvoiceService.generateInvoice(3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Customer is on credit hold");
    }
}
