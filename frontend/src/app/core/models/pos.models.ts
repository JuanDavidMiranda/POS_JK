export type TaxType = 'IVA' | 'EXENTO';
export type PaymentMethod = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA';
export type SaleStatus = 'OFFLINE_PENDING' | 'SYNCED' | 'REJECTED_CONFLICT' | 'VOIDED' | 'PARTIALLY_RETURNED';
export type MovementType =
  | 'INITIAL_BALANCE'
  | 'PURCHASE_IN'
  | 'SALE_OUT'
  | 'CREDIT_NOTE_IN'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN';
export type CreditNoteReason = '1' | '2' | '3';
export type TransferStatus = 'DRAFT' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED';

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

export interface Branch {
  id: string;
  code: string;
  name: string;
  address?: string | null;
  city?: string | null;
  phone?: string | null;
  main: boolean;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface TerminalInfo {
  id: string;
  branchId: string;
  branchCode?: string | null;
  branchName?: string | null;
  name: string;
  dianPrefix: string;
  rangeFrom: number;
  rangeTo: number;
  lastSyncedSequence: number;
  creditNotePrefix: string;
  creditNoteLastSequence: number;
  active: boolean;
  updatedAt?: string;
}

export interface TerminalConfig {
  id: string;
  branchId: string;
  branchCode?: string | null;
  branchName?: string | null;
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

export interface SecurityPolicy {
  requirePinForDiscounts: boolean;
  maxCashierDiscountPercent: number;
  requirePinForVoidItem: boolean;
  blindCashClose: boolean;
  updatedAt?: string;
}

export interface SupervisorUser {
  userId: string;
  fullName: string;
  pinHash: string;
}

export interface ConfigRecord {
  key: string;
  value: unknown;
  updatedAt: string;
}

export interface CashShiftRecord {
  id: string;
  terminalId?: string | null;
  status: 'OPEN' | 'CLOSED';
  openingAmount: number;
  expectedCashAmount?: number | null;
  countedCashAmount?: number | null;
  differenceAmount?: number | null;
  openedAt: string;
  closedAt?: string | null;
  authorizedByUserId?: string | null;
  notes?: string | null;
  synced: boolean;
}

export interface CartLine {
  id?: string;
  productId: string;
  name: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  taxRate: number;
  discountPercent: number;
  discountAmount: number;
  currentStock: number;
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
  customerId?: string;
  documentType: 'POS';
  dianPrefix: string;
  sequenceNumber: number;
  fullDocumentNumber: string;
  dianStatus: 'PENDING';
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  grandTotal: number;
  paymentMethod: PaymentMethod;
  authorizedBy?: string | null;
  cashShiftId?: string | null;
  soldAt: string;
  items: SaleItemPayload[];
}

export interface SaleHistoryRecord {
  id: string;
  fullDocumentNumber: string;
  sequenceNumber: number;
  status: SaleStatus;
  paymentMethod: PaymentMethod;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  grandTotal: number;
  soldAt: string;
  authorizedBy?: string | null;
  cashShiftId?: string | null;
  terminalId?: string;
  items: any[];
  rejectionReason?: string;
  lastError?: string;
  creditNotes?: CreditNoteRecord[];
}

export interface OutboxEvent {
  idempotencyKey: string;
  entityType: 'SALE';
  payload: SalePayload;
  createdAt: string;
  attemptCount: number;
  lastError?: string;
}

export interface SyncEventResult {
  idempotencyKey: string;
  status: 'PROCESSED' | 'DUPLICATE' | 'DUPLICATE_IGNORED' | 'REJECTED_CONFLICT';
  saleId?: string | null;
  fullDocumentNumber?: string | null;
  reason?: string;
  message?: string;
}

export interface PullResponse {
  serverTime: string;
  batchId: string;
  tenantId: string;
  terminal: TerminalConfig;
  products: Product[];
  customers: Customer[];
  securityPolicy?: SecurityPolicy;
  supervisorPins?: SupervisorUser[];
}

export interface PushResponse {
  batchId: string;
  processedAt: string;
  results: SyncEventResult[];
}

export interface InventoryMovementRecord {
  id: string;
  branchId?: string | null;
  productId: string;
  movementType: MovementType;
  type?: MovementType;
  quantity: number;
  previousStock?: number;
  newStock?: number;
  resultingStock?: number;
  sku?: string;
  productName?: string;
  notes?: string | null;
  reason?: string | null;
  referenceId?: string | null;
  createdAt: string;
  createdBy?: string | null;
  pendingSync?: boolean;
}

export interface CreditNoteItemPayload {
  saleItemId?: string;
  productId: string;
  quantity: number;
  unitPrice: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
}

export interface CreditNoteRecord {
  id: string;
  saleId: string;
  fullDocumentNumber: string;
  reasonCode: CreditNoteReason;
  reasonDescription: string;
  dianStatus: string;
  subtotal: number;
  taxTotal: number;
  grandTotal: number;
  createdAt: string;
  items: CreditNoteItemPayload[];
  cude?: string;
  saleStatus?: string;
}

export interface SaleSummaryResponse {
  grossSales: number;
  creditNotesTotal: number;
  netSales: number;
  taxTotal: number;
  transactions: number;
  averageTicket: number;
  paymentBreakdown: { paymentMethod: string; grossTotal: number; transactions: number }[];
  dailyBreakdown: { date: string; grossSales: number; creditNotes: number; netSales: number; taxTotal: number; transactions: number }[];
}

export interface TopProductsResponse {
  totalRevenue: number;
  totalUnits: number;
  items: { productId: string; sku: string; name: string; unitsSold: number; revenue: number; participationPercent: number }[];
}

export interface TaxSummaryResponse {
  taxableBaseTotal: number;
  taxAmountTotal: number;
  grandTotal: number;
  rows: { label: string; taxType: string; taxRate: number; taxableBase: number; taxAmount: number; total: number }[];
}

export const round2 = (value: number): number => Math.round((value + Number.EPSILON) * 100) / 100;

const moneyFormatter = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

export const money = (value: number): string => moneyFormatter.format(value ?? 0);

export interface LowStockProduct {
  id: string;
  branchId?: string;
  sku?: string;
  name: string;
  currentStock: number;
  minStock: number;
  allowNegativeStock?: boolean;
}

export interface InventoryTransferItem {
  id?: string;
  productId: string;
  sku?: string | null;
  productName?: string | null;
  quantity: number;
}

export interface InventoryTransfer {
  id: string;
  transferNumber: string;
  sourceBranchId: string;
  sourceBranchName?: string | null;
  destinationBranchId: string;
  destinationBranchName?: string | null;
  status: TransferStatus;
  notes?: string | null;
  createdBy?: string | null;
  receivedBy?: string | null;
  createdAt: string;
  updatedAt?: string;
  dispatchedAt?: string | null;
  receivedAt?: string | null;
  cancelledAt?: string | null;
  itemCount?: number;
  items?: InventoryTransferItem[];
}