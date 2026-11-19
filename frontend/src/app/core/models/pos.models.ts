export type TaxType = 'IVA' | 'EXENTO';
export type PaymentMethod = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA';
export type SaleStatus = 'OFFLINE_PENDING' | 'SYNCED' | 'REJECTED_CONFLICT' | 'VOIDED' | 'PARTIALLY_RETURNED';
export type MovementType =
  | 'INITIAL_BALANCE'
  | 'PURCHASE_IN'
  | 'SALE_OUT'
  | 'CREDIT_NOTE_IN'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT';
export type CreditNoteReason = '1' | '2' | '3';

export interface Product {
  id: string;
  barcode?: string;
  sku: string;
  name: string;
  salePrice: number;
  taxType: TaxType;
  taxRate: number;
  currentStock: number;
  minStock: number;
  updatedAt: string;
}

export interface Customer {
  id: string;
  documentType: string;
  documentNumber: string;
  fullName: string;
  email?: string;
  updatedAt: string;
}

export interface TerminalConfig {
  id: string;
  name: string;
  dianPrefix: string;
  rangeFrom: number;
  rangeTo: number;
  lastSyncedSequence: number;
  nextSequence: number;
  creditNotePrefix: string;
  creditNoteLastSequence: number;
  creditNoteNextSequence: number;
}

export interface ConfigRecord {
  key: string;
  value: unknown;
  updatedAt: string;
}

export interface CartLine {
  productId: string;
  name: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  taxRate: number;
  taxType: TaxType;
  lineSubtotal: number;
  lineTax: number;
  lineTotal: number;
}

export interface SaleItemPayload {
  id: string;
  productId: string;
  quantity: number;
  unitPrice: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
}

export interface SalePayload {
  id: string;
  terminalId: string;
  customerId: string | null;
  documentType: string;
  dianPrefix: string;
  sequenceNumber: number;
  fullDocumentNumber: string;
  dianStatus: string;
  subtotal: number;
  taxTotal: number;
  grandTotal: number;
  paymentMethod: PaymentMethod;
  soldAt: string;
  items: SaleItemPayload[];
}

export interface SaleHistoryRecord extends SalePayload {
  status: SaleStatus;
  syncedAt?: string;
  lastError?: string;
  creditNotes?: CreditNoteRecord[];
}

export interface InventoryMovementRecord {
  id: string;
  productId: string;
  sku?: string;
  productName?: string;
  movementType: MovementType;
  quantity: number;
  previousStock: number;
  newStock: number;
  referenceId?: string | null;
  notes?: string | null;
  createdBy?: string | null;
  createdAt: string;
  pendingSync?: boolean;
}

export interface LowStockProduct {
  id: string;
  sku: string;
  name: string;
  currentStock: number;
  minStock: number;
  allowNegativeStock: boolean;
}

export interface CreditNoteItem {
  id?: string;
  productId: string;
  quantity: number;
  unitPrice?: number;
  taxAmount?: number;
  lineTotal?: number;
}

export interface CreditNoteRecord {
  id: string;
  saleId: string;
  terminalId: string;
  dianPrefix: string;
  sequenceNumber: number;
  fullDocumentNumber: string;
  reasonCode: CreditNoteReason;
  reasonDescription: string;
  dianStatus: 'PENDING_TRANSMISSION' | 'ACCEPTED' | 'REJECTED';
  cude?: string | null;
  subtotal: number;
  taxTotal: number;
  grandTotal: number;
  createdAt: string;
  saleStatus?: string;
  items: CreditNoteItem[];
}

export interface OutboxEvent {
  idempotencyKey: string;
  entityType: 'SALE';
  payload: SalePayload;
  createdAt: string;
  attempts: number;
  lastError?: string;
}

export interface PullResponse {
  serverTime: string;
  batchId: string;
  tenantId: string;
  terminal: TerminalConfig;
  products: Product[];
  customers: Customer[];
}

export interface SyncEventResult {
  idempotencyKey: string;
  status: 'PROCESSED' | 'DUPLICATE_IGNORED' | 'REJECTED_CONFLICT';
  saleId: string | null;
  fullDocumentNumber: string | null;
  message: string;
}

export interface PushResponse {
  batchId: string;
  processedAt: string;
  results: SyncEventResult[];
}

export function money(value: number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(value);
}

export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}
