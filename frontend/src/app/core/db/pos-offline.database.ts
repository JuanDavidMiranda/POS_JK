import Dexie, { type EntityTable } from 'dexie';
import {
  type ConfigRecord,
  type Customer,
  type OutboxEvent,
  type Product,
  type SaleHistoryRecord,
} from '../models/pos.models';

class PosOfflineDatabase extends Dexie {
  products!: EntityTable<Product, 'id'>;
  customers!: EntityTable<Customer, 'id'>;
  config!: EntityTable<ConfigRecord, 'key'>;
  outbox!: EntityTable<OutboxEvent, 'idempotencyKey'>;
  salesHistory!: EntityTable<SaleHistoryRecord, 'id'>;

  constructor() {
    super('PosOfflineDatabase');
    this.version(1).stores({
      products: 'id, sku, barcode, name, updatedAt',
      customers: 'id, documentNumber, updatedAt',
      config: 'key',
      outbox: 'idempotencyKey, createdAt, entityType',
      salesHistory: 'id, fullDocumentNumber, sequenceNumber, status, soldAt',
    });
  }
}

export const db = new PosOfflineDatabase();

/** Pide al navegador que no expulse IndexedDB bajo presión de almacenamiento. */
export async function requestPersistentStorage(): Promise<boolean> {
  if (!navigator.storage?.persist) {
    return false;
  }
  try {
    if (await navigator.storage.persisted()) {
      return true;
    }
    return await navigator.storage.persist();
  } catch {
    return false;
  }
}

export async function readConfig<T>(key: string): Promise<T | undefined> {
  const record = await db.config.get(key);
  return record?.value as T | undefined;
}

export function sequenceKey(prefix: string): string {
  return `sequence:${prefix}`;
}
