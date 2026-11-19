package com.possaas.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Proyección de cantidad ya devuelta por producto para una venta. */
public interface ReturnedQuantity {
    UUID getProductId();

    BigDecimal getQuantity();
}
