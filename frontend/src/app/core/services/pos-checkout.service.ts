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

  /**
   * Operación ACID en IndexedDB: consecutivo, stock, histórico y outbox se escriben
   * en una sola transacción Dexie. Funciona exactamente igual sin red.
   */
  async checkout(
    cart: CartLine[],
    paymentMethod: PaymentMethod,
    terminal: TerminalConfig,
  ): Promise<SaleHistoryRecord> {
    if (cart.length === 0) {
      throw new Error('El carrito está vacío');
    }

    const saleId = uuidv7();
    const soldAt = new Date().toISOString();
    const customer = await this.defaultCustomer();

    return db.transaction('rw', db.config, db.products, db.outbox, db.salesHistory, async () => {
      const key = sequenceKey(terminal.dianPrefix);
      const stored = await db.config.get(key);
      const current = typeof stored?.value === 'number' ? stored.value : terminal.lastSyncedSequence;
      const nextSequence = current + 1;

      if (nextSequence < terminal.rangeFrom || nextSequence > terminal.rangeTo) {
        throw new Error('Rango autorizado DIAN agotado para esta caja');
      }

      for (const line of cart) {
        const product = await db.products.get(line.productId);
        if (!product) {
          throw new Error(`Producto no disponible localmente: ${line.name}`);
        }
        if (product.currentStock < line.quantity) {
          throw new Error(`Stock insuficiente para ${product.name}`);
        }
      }

      for (const line of cart) {
        await db.products.where('id').equals(line.productId).modify((product) => {
          product.currentStock = round2(product.currentStock - line.quantity);
          product.updatedAt = soldAt;
        });
      }

      const items: SaleItemPayload[] = cart.map((line) => ({
        id: uuidv7(),
        productId: line.productId,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        taxRate: line.taxRate,
        taxAmount: line.lineTax,
        lineTotal: line.lineTotal,
      }));

      const subtotal = round2(cart.reduce((sum, line) => sum + line.lineSubtotal, 0));
      const taxTotal = round2(cart.reduce((sum, line) => sum + line.lineTax, 0));

      const payload: SalePayload = {
        id: saleId,
        terminalId: terminal.id,
        customerId: customer?.id ?? null,
        documentType: 'FV',
        dianPrefix: terminal.dianPrefix,
        sequenceNumber: nextSequence,
        fullDocumentNumber: `${terminal.dianPrefix}-${String(nextSequence).padStart(6, '0')}`,
        dianStatus: 'PENDING',
        subtotal,
        taxTotal,
        grandTotal: round2(subtotal + taxTotal),
        paymentMethod,
        soldAt,
        items,
      };

      const outboxEvent: OutboxEvent = {
        idempotencyKey: uuidv7(),
        entityType: 'SALE',
        payload,
        createdAt: soldAt,
        attempts: 0,
      };

      const history: SaleHistoryRecord = {
        ...payload,
        status: 'OFFLINE_PENDING',
      };

      await db.config.put({ key, value: nextSequence, updatedAt: soldAt });
      await db.salesHistory.put(history);
      await db.outbox.put(outboxEvent);

      return history;
    });
  }

  private async defaultCustomer(): Promise<Customer | undefined> {
    return db.customers.orderBy('documentNumber').first();
  }
}
