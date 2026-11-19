package com.possaas.service;

import com.possaas.domain.CreditNote;
import com.possaas.domain.Sale;
import com.possaas.dto.DianCreditNoteResult;

import java.util.UUID;

/** Abstracción para transmisión a la DIAN u otro proveedor de facturación electrónica. */
public interface ElectronicBillingProvider {

    DianCreditNoteResult sendCreditNote(UUID tenantId, CreditNote creditNote, Sale sale);
}
