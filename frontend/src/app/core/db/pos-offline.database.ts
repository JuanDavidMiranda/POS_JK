import Dexie, { type EntityTable } from 'dexie';
import {
  type Branch,
  type CashShiftRecord,
  type ConfigRecord,
  type CreditNoteRecord,
  type Customer,
  type InventoryMovementRecord,
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
  inventoryMovements!: EntityTable<InventoryMovementRecord, 'id'>;
  creditNotes!: EntityTable<CreditNoteRecord, 'id'>;
  cashShifts!: EntityTable<CashShiftRecord, 'id'>;
  branches!: EntityTable<Branch, 'id'>;

  constructor() {
    super('PosOfflineDatabase');
    this.version(1).stores({
      products: 'id, sku, barcode, name, updatedAt',
      customers: 'id, documentNumber, updatedAt',
      config: 'key',
      outbox: 'idempotencyKey, createdAt, entityType',
      salesHistory: 'id, fullDocumentNumber, sequenceNumber, status, soldAt',
    });
    this.version(2).stores({
      products: 'id, sku, barcode, name, updatedAt, minStock',
      customers: 'id, documentNumber, updatedAt',
      config: 'key',
      outbox: 'idempotencyKey, createdAt, entityType',
      salesHistory: 'id, fullDocumentNumber, sequenceNumber, status, soldAt',
      inventoryMovements: 'id, productId, createdAt',
      creditNotes: 'id, saleId, createdAt',
    });
    this.version(3).stores({
      products: 'id, sku, barcode, name, updatedAt, minStock',
      customers: 'id, documentNumber, updatedAt',
      config: 'key',
      outbox: 'idempotencyKey, createdAt, entityType',
      salesHistory: 'id, fullDocumentNumber, sequenceNumber, status, soldAt, cashShiftId',
      inventoryMovements: 'id, productId, createdAt',
      creditNotes: 'id, saleId, createdAt',
      cashShifts: 'id, status, openedAt, closedAt',
    });
    this.version(4).stores({
      products: 'id, sku, barcode, name, updatedAt, minStock',
      customers: 'id, documentNumber, updatedAt',
      config: 'key',
      outbox: 'idempotencyKey, createdAt, entityType',
      salesHistory: 'id, fullDocumentNumber, sequenceNumber, status, soldAt, cashShiftId',
      inventoryMovements: 'id, productId, branchId, createdAt',
      creditNotes: 'id, saleId, createdAt',
      cashShifts: 'id, status, openedAt, closedAt',
      branches: 'id, code, name, isMain, isActive',
    }).upgrade(async (tx) => {
      // Limpia catǭlogo cacheado para forzar pull branch-aware en el siguiente sync
      await tx.table('products').clear();
      await tx.table('config').delete('last_pull_cursor');
    });
  }
}

export const db = new PosOfflineDatabase();

export const terminalConfigKey = 'terminal_config';
export const lastPullKey = 'last_pull_cursor';
export const demoTenantKey = 'tenant_id';
export const securityPolicyKey = 'security_policy';
export const supervisorPinsKey = 'supervisor_pins';
export const selectedAdminBranchKey = 'selected_admin_branch_id';
export const subscriptionSnapshotKey = 'subscription_snapshot';

export async function readConfig<T>(key: string): Promise<T | undefined> {
  const record = await db.config.get(key);
  return record?.value as T | undefined;
}

export async function writeConfig(key: string, value: unknown): Promise<void> {
  await db.config.put({ key, value, updatedAt: new Date().toISOString() });
}

export function sequenceKey(prefix: string): string {
  return `sequence:${prefix}`;
}

export async function requestPersistentStorage(): Promise<boolean> {
  if (!navigator.storage?.persist) {
    return false;
  }
  return navigator.storage.persist();
}
