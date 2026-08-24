package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.PaymentScheduleDTO;
import com.justjava.ams.accountant.entity.PaymentSchedule;
import com.justjava.ams.accountant.entity.PurchaseInvoice;
import com.justjava.ams.accountant.entity.Vendor;
import com.justjava.ams.accountant.repository.PaymentScheduleRepository;
import com.justjava.ams.accountant.repository.PurchaseInvoiceRepository;
import com.justjava.ams.common.entity.Organization;
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
class PaymentScheduleServiceTest {

    @Mock
    private PaymentScheduleRepository paymentScheduleRepository;

    @Mock
    private PurchaseInvoiceRepository purchaseInvoiceRepository;

    @InjectMocks
    private PaymentScheduleService paymentScheduleService;

    @Test
    void createsDefaultScheduleForPostedInvoiceOutstandingBalance() {
        Organization organization = Organization.builder().id(1L).build();
        Vendor vendor = Vendor.builder().id(2L).legalName("Vendor Ltd").organization(organization).build();
        PurchaseInvoice invoice = PurchaseInvoice.builder()
                .id(10L)
                .organization(organization)
                .vendor(vendor)
                .purchaseOrderNumber("PI-10")
                .dueDate(LocalDate.of(2026, 2, 1))
                .totalAmount(new BigDecimal("500.00"))
                .amountPaid(new BigDecimal("125.00"))
                .vendorName("Vendor Ltd")
                .build();

        when(purchaseInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        when(paymentScheduleRepository.findByPurchaseInvoiceIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(paymentScheduleRepository.save(any(PaymentSchedule.class))).thenAnswer(invocation -> {
            PaymentSchedule schedule = invocation.getArgument(0);
            schedule.setId(30L);
            return schedule;
        });

        PaymentScheduleDTO dto = paymentScheduleService.createDefaultScheduleForInvoice(10L, "poster");

        assertThat(dto.getPurchaseInvoiceId()).isEqualTo(10L);
        assertThat(dto.getScheduledPaymentDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(dto.getAmountRemaining()).isEqualByComparingTo("375.00");
        assertThat(dto.getStatus()).isEqualTo("PLANNED");
    }

    @Test
    void directPaymentReducesActiveSchedules() {
        Organization organization = Organization.builder().id(1L).build();
        PurchaseInvoice invoice = PurchaseInvoice.builder().id(10L).organization(organization).build();
        PaymentSchedule schedule = PaymentSchedule.builder()
                .id(30L)
                .purchaseInvoice(invoice)
                .amountRemaining(new BigDecimal("300.00"))
                .status(PaymentSchedule.ScheduleStatus.APPROVED)
                .build();
        when(paymentScheduleRepository.findByPurchaseInvoiceIdAndStatusIn(any(), any())).thenReturn(List.of(schedule));
        when(paymentScheduleRepository.save(any(PaymentSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentScheduleService.applyInvoicePayment(invoice, new BigDecimal("300.00"));

        assertThat(schedule.getAmountRemaining()).isEqualByComparingTo("0.00");
        assertThat(schedule.getStatus()).isEqualTo(PaymentSchedule.ScheduleStatus.PAID);
    }
}
