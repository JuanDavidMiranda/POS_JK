package com.possaas.service;

import com.possaas.domain.CreditNote;
import com.possaas.domain.Sale;
import com.possaas.dto.DianCreditNoteResult;

import java.time.LocalDate;
import java.util.UUID;

/** Abstraccion para transmision a la DIAN u otro proveedor de facturacion electronica. */
public interface ElectronicBillingProvider {

    DianCreditNoteResult sendCreditNote(UUID tenantId, CreditNote creditNote, Sale sale);

    default DianVerificationResult verifyConfiguration(DianVerificationRequest request) {
        return new DianVerificationResult(true, "SIMULATED", "Configuracion DIAN aceptada (simulada)");
    }

    record DianVerificationRequest(
            UUID tenantId,
            String prefix,
            Long rangeFrom,
            Long rangeTo,
            String resolutionNumber,
            LocalDate resolutionDate,
            LocalDate validFrom,
            LocalDate validTo,
            String technicalKey
    ) {}

    record DianVerificationResult(boolean ok, String mode, String message) {}
}
