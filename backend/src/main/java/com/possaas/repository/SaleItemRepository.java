package com.possaas.repository;

import com.possaas.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, java.util.UUID> {
}
