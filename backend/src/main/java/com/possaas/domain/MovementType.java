package com.possaas.domain;

/** Tipos de movimiento del kardex. Coinciden con el CHECK de inventory_movements. */
public enum MovementType {
    INITIAL_BALANCE,
    PURCHASE_IN,
    SALE_OUT,
    CREDIT_NOTE_IN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT
}
