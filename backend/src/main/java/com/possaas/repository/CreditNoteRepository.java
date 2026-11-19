package com.possaas.repository;

import com.possaas.domain.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByTenantIdAndSaleIdOrderByCreatedAtDesc(UUID tenantId, UUID saleId);
}
