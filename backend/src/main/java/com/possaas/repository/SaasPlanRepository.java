package com.possaas.repository;

import com.possaas.domain.SaasPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaasPlanRepository extends JpaRepository<SaasPlan, String> {
    List<SaasPlan> findByActiveTrueOrderByMonthlyPriceCopAsc();
}
