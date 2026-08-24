package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.CollectionCaseActionRequest;
import com.justjava.ams.accountant.dto.ReceivablesCollectionCaseDTO;
import com.justjava.ams.accountant.entity.*;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivablesCollectionServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;

    @Mock
    private ReceivablesCollectionCaseRepository collectionCaseRepository;

    @Mock
    private CollectionActivityRepository activityRepository;

    @Mock
    private PromiseToPayRepository promiseRepository;

    @Mock
    private CustomerStatementRepository customerStatementRepository;

    @Mock
    private CustomerStatementLineRepository customerStatementLineRepository;

    @InjectMocks
    private ReceivablesCollectionService service;

    @Test
    void generatesCollectionCaseForOverdueReceivable() {
        Organization organization = Organization.builder().id(1L).build();
        Customer customer = Customer.builder().id(2L).organization(organization).legalName("Acme Plc").build();
        CustomerInvoice invoice = CustomerInvoice.builder()
                .id(3L)
                .organization(organization)
                .customer(customer)
                .customerName("Acme Plc")
                .invoiceNumber("AR-3")
                .invoiceDate(LocalDate.of(2026, 1, 1))
                .dueDate(LocalDate.of(2026, 1, 31))
                .totalAmount(new BigDecimal("1000.00"))
                .amountPaid(new BigDecimal("250.00"))
                .status(CustomerInvoice.InvoiceStatus.POSTED)
                .build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(customerInvoiceRepository.findByOrganizationId(1L)).thenReturn(List.of(invoice));
        when(collectionCaseRepository.findByCustomerInvoiceId(3L)).thenReturn(Optional.empty());
        when(collectionCaseRepository.save(any(ReceivablesCollectionCase.class))).thenAnswer(invocation -> {
            ReceivablesCollectionCase collectionCase = invocation.getArgument(0);
            collectionCase.setId(10L);
            return collectionCase;
        });
        when(collectionCaseRepository.findByOrganizationIdOrderByDaysOverdueDescIdDesc(1L))
                .thenAnswer(invocation -> List.of(ReceivablesCollectionCase.builder()
                        .id(10L)
                        .organization(organization)
                        .customer(customer)
                        .customerInvoice(invoice)
                        .outstandingAmount(new BigDecimal("750.00"))
                        .dueDate(invoice.getDueDate())
                        .daysOverdue(9L)
                        .status(ReceivablesCollectionCase.CaseStatus.OPEN)
                        .dunningLevel(0)
                        .build()));
        when(activityRepository.findByCollectionCaseIdOrderByCreatedAtDescIdDesc(10L)).thenReturn(List.of());
        when(promiseRepository.findByCollectionCaseIdOrderByPromisedDateDescIdDesc(10L)).thenReturn(List.of());
        when(promiseRepository.findByCollectionCaseIdAndStatusIn(10L, List.of(PromiseToPay.PromiseStatus.ACTIVE))).thenReturn(List.of());

        List<ReceivablesCollectionCaseDTO> cases = service.generateCases(1L, LocalDate.of(2026, 2, 9));

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getOutstandingAmount()).isEqualByComparingTo("750.00");
        assertThat(cases.get(0).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void createsPromiseAndMovesCaseToPromised() {
        Organization organization = Organization.builder().id(1L).build();
        CustomerInvoice invoice = CustomerInvoice.builder()
                .id(3L)
                .organization(organization)
                .customerName("Acme Plc")
                .invoiceNumber("AR-3")
                .invoiceDate(LocalDate.of(2026, 1, 1))
                .dueDate(LocalDate.of(2026, 1, 31))
                .totalAmount(new BigDecimal("1000.00"))
                .amountPaid(BigDecimal.ZERO)
                .build();
        ReceivablesCollectionCase collectionCase = ReceivablesCollectionCase.builder()
                .id(10L)
                .organization(organization)
                .customerInvoice(invoice)
                .outstandingAmount(new BigDecimal("1000.00"))
                .dueDate(invoice.getDueDate())
                .daysOverdue(10L)
                .status(ReceivablesCollectionCase.CaseStatus.OPEN)
                .build();

        when(collectionCaseRepository.findById(10L)).thenReturn(Optional.of(collectionCase));
        when(promiseRepository.save(any(PromiseToPay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(collectionCaseRepository.save(any(ReceivablesCollectionCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(CollectionActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.findByCollectionCaseIdOrderByCreatedAtDescIdDesc(10L)).thenReturn(List.of());
        when(promiseRepository.findByCollectionCaseIdOrderByPromisedDateDescIdDesc(10L)).thenReturn(List.of());

        ReceivablesCollectionCaseDTO dto = service.createPromise(
                10L,
                CollectionCaseActionRequest.builder()
                        .promisedAmount(new BigDecimal("500.00"))
                        .promisedDate(LocalDate.of(2026, 2, 20))
                        .build(),
                "collector");

        assertThat(dto.getStatus()).isEqualTo("PROMISED");
        assertThat(dto.getNextActionDate()).isEqualTo(LocalDate.of(2026, 2, 20));
    }
}
