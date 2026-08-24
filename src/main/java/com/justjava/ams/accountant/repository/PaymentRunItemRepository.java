package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.PaymentRunItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentRunItemRepository extends JpaRepository<PaymentRunItem, Long> {
    List<PaymentRunItem> findByPaymentRunIdOrderByIdAsc(Long paymentRunId);
    boolean existsByPaymentScheduleIdAndStatusIn(Long paymentScheduleId, Collection<PaymentRunItem.PaymentRunItemStatus> statuses);
}
