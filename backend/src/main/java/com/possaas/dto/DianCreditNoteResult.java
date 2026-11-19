package com.possaas.dto;

/** Resultado devuelto por el proveedor de facturación electrónica. */
public record DianCreditNoteResult(String status, String cude, String message) {
}
