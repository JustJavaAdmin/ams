package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.ExpenseLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseLineItemRepository extends JpaRepository<ExpenseLineItem, Long> {
    List<ExpenseLineItem> findByExpenseId(Long expenseId);
}
