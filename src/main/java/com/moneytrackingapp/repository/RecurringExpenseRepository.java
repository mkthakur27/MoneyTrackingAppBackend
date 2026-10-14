package com.moneytrackingapp.repository;

import com.moneytrackingapp.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {
}
