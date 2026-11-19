import { Injectable } from '@angular/core';
import { v7 as uuidv7 } from 'uuid';
import { db, sequenceKey } from '../db/pos-offline.database';
import {
  round2,
  type CartLine,
  type Customer,
  type OutboxEvent,
  type PaymentMethod,
  type SaleHistoryRecord,
  type SaleItemPayload,
  type SalePayload,
  type TerminalConfig,
} from '../models/pos.models';

@Injectable({ providedIn: 'root' })
export class PosCheckoutService {

  computeTotals(cart: CartLine[]): { subtotal: number; discountTotal: number; taxTotal: number; grandTotal: number } {
    let subtotal = 0;
    let discountTotal = 0;
    let taxTotal = 0;
    for (const item of cart) {
      const lineSubtotal = round2(item.quantity * item.unitPrice);
      const lineDiscount = this.lineDiscount(item, lineSubtotal);
      const taxableBase = Math.max(0, round2(lineSubtotal - lineDiscount));
      subtotal = round2(subtotal + lineSubtotal);
      discountTotal = round2(discountTotal + lineDiscount);
      // Impuesto calculado sobre base antes de descuento para compatibilidad con backend (ítems envían subtotal/tax por precio completo)
      // pero el total a pagar descuenta el valor de descuento.
      taxTotal = round2(taxTotal + round2(lineSubtotal * item.taxRate));
    }
    const grandTotal = Math.max(0, round2(subtotal - discountTotal + taxTotal));
    return { subtotal, discountTotal, taxTotal, grandTotal };
  }

  lineDiscount(item: CartLine, lineSubtotal?: number): number {
    const base = lineSubtotal ?? round2(item.quantity * item.unitPrice);
    const pct = Math.max(0, Number(item.discountPercent || 0));
    const pctAmount = round2(base * (pct / 100));
    const fixed = Math.max(0, Number(item.discountAmount || 0));
    return Math.min(base, round2(pctAmount + fixed));
  }

  /**
   * Operación ACID en IndexedDB: consecutivo, stock, histórico y outbox se escriben
   * en una sola transacción Dexie. Funciona exactamente igual sin red.
   */
  async checkout(
    cart: CartLine[],
    paymentMethod: PaymentMethod,
    terminal: TerminalConfig,
    options?: { authorizedBy?: string | null; cashShiftId?: string | null },
  ): Promise<SaleHistoryRecord> {
    if (cart.length === 0) {
      throw new Error('El carrito está vacío');
    }

    const saleId = uuidv7();
    const soldAt = new Date().toISOString();
    const customer = await this.defaultCustomer();
    const totals = this.computeTotals(cart);
    const authorizedBy = options?.authorizedBy ?? null;
    const cashShiftId = options?.cashShiftId ?? null;

    return db.transaction('rw', db.config, db.products, db.outbox, db.salesHistory, async () => {
      const key = sequenceKey(terminal.dianPrefix);
      const stored = await db.config.get(key);
      const current = typeof stored?.value === 'number' ? stored.value : terminal.lastSyncedSequence;
      if (current >= terminal.rangeTo) {
        throw new Error('Rango DIAN agotado. Solicite una nueva resolución para seguir facturando.');
      }

      const sequenceNumber = current + 1;
      const fullDocumentNumber = `${terminal.dianPrefix}-${sequenceNumber}`;

      for (const item of cart) {
        const product = await db.products.get(item.productId);
        if (!product) {
          throw new Error(`Producto no encontrado: ${item.name}`);
        }
        if (product.currentStock < item.quantity) {
          throw new Error(`Stock insuficiente para ${item.name}`);
        }
      }

      const payloadItems: SaleItemPayload[] = cart.map((item) => {
        const lineSubtotal = round2(item.quantity * item.unitPrice);
        const taxAmount = round2(lineSubtotal * item.taxRate);
        return {
          id: uuidv7(),
          productId: item.productId,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          taxRate: item.taxRate,
          taxAmount,
          lineTotal: round2(lineSubtotal + taxAmount),
        };
      });

      const payload: SalePayload = {
        id: saleId,
        terminalId: terminal.id,
        customerId: customer?.id,
        documentType: 'POS',
        dianPrefix: terminal.dianPrefix,
        sequenceNumber,
        fullDocumentNumber,
        dianStatus: 'PENDING',
        subtotal: totals.subtotal,
        discountTotal: totals.discountTotal,
        taxTotal: totals.taxTotal,
        grandTotal: totals.grandTotal,
        paymentMethod,
        authorizedBy,
        cashShiftId,
        soldAt,
        items: payloadItems,
      };

      const historyRecord: SaleHistoryRecord = {
        id: saleId,
        fullDocumentNumber,
        sequenceNumber,
        status: 'OFFLINE_PENDING',
        paymentMethod,
        subtotal: totals.subtotal,
        discountTotal: totals.discountTotal,
        taxTotal: totals.taxTotal,
        grandTotal: totals.grandTotal,
        soldAt,
        authorizedBy,
        cashShiftId,
        items: cart.map((i) => ({ ...i })),
      };

      const event: OutboxEvent = {
        idempotencyKey: saleId,
        entityType: 'SALE',
        payload,
        createdAt: soldAt,
        attemptCount: 0,
      };

      for (const item of cart) {
        await db.products
          .where('id')
          .equals(item.productId)
          .modify((product) => {
            product.currentStock = round2(product.currentStock - item.quantity);
          });
      }

      await db.config.put({ key, value: sequenceNumber, updatedAt: soldAt });
      await db.salesHistory.put(historyRecord);
      await db.outbox.put(event);

      return historyRecord;
    });
  }

  private async defaultCustomer(): Promise<Customer | undefined> {
    return db.customers
      .filter((customer) => customer.documentNumber === '222222222222')
      .first();
  }
}