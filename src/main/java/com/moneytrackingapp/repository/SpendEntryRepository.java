package com.moneytrackingapp.repository;

import com.moneytrackingapp.model.SpendEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpendEntryRepository extends JpaRepository<SpendEntry, Long> {
}
