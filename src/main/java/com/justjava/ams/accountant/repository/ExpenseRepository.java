package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByOrganizationId(Long organizationId);
    Optional<Expense> findByOrganizationIdAndExpenseNumber(Long organizationId, String expenseNumber);
}
