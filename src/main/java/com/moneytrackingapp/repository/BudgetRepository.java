package com.moneytrackingapp.repository;

import com.moneytrackingapp.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
}
