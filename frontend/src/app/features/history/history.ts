import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { v7 as uuidv7 } from 'uuid';
import { db } from '../../core/db/pos-offline.database';
import {
  money,
  type CreditNoteReason,
  type CreditNoteRecord,
  type SaleHistoryRecord,
  type TerminalConfig,
} from '../../core/models/pos.models';
import { CreditNoteService } from '../../core/services/credit-note.service';
import { SyncService } from '../../core/services/sync.service';

@Component({
  selector: 'app-history',
  templateUrl: './history.html',
  styleUrl: './history.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class History implements OnInit {
  readonly sync = inject(SyncService);
  private readonly creditNoteService = inject(CreditNoteService);
  readonly sales = signal<SaleHistoryRecord[]>([]);
  readonly money = money;

  readonly modalSale = signal<SaleHistoryRecord | null>(null);
  readonly reasonCode = signal<CreditNoteReason>('2');
  readonly reasonDescription = signal('Anulación de factura');
  readonly returnQty = signal<Record<string, number>>({});
  readonly busy = signal(false);
  readonly feedback = signal('');
  readonly lastNote = signal<CreditNoteRecord | null>(null);
  readonly terminal = signal<TerminalConfig | null>(null);

  readonly modalTotal = computed(() => {
    const sale = this.modalSale();
    if (!sale) return 0;
    const qty = this.returnQty();
    let total = 0;
    for (const item of sale.items) {
      const q = qty[item.productId] ?? 0;
      total += q * item.unitPrice * (1 + item.taxRate);
    }
    return Math.round(total * 100) / 100;
  });

  constructor() {
    effect(() => {
      if (this.sync.lastSyncAt()) {
        void this.reload();
      }
    });
  }

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.sales.set(await db.salesHistory.orderBy('soldAt').reverse().toArray());
    this.terminal.set((await this.sync.terminal()) ?? null);
  }

  async refresh(): Promise<void> {
    await this.sync.synchronize();
    await this.reload();
  }

  formatDate(iso: string): string {
    return new Intl.DateTimeFormat('es-CO', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(iso));
  }

  canVoid(sale: SaleHistoryRecord): boolean {
    return sale.status === 'SYNCED' || sale.status === 'PARTIALLY_RETURNED';
  }

  returnedQty(sale: SaleHistoryRecord, productId: string): number {
    return (sale.creditNotes ?? [])
      .flatMap((n) => n.items)
      .filter((i) => i.productId === productId)
      .reduce((sum, i) => sum + i.quantity, 0);
  }

  openVoid(sale: SaleHistoryRecord): void {
    this.modalSale.set(sale);
    this.reasonCode.set('2');
    this.reasonDescription.set('Anulación de factura');
    this.feedback.set('');
    const initial: Record<string, number> = {};
    for (const item of sale.items) {
      const remaining = item.quantity - this.returnedQty(sale, item.productId);
      initial[item.productId] = remaining;
    }
    this.returnQty.set(initial);
  }

  closeVoid(): void {
    this.modalSale.set(null);
  }

  setReason(code: CreditNoteReason): void {
    this.reasonCode.set(code);
    const sale = this.modalSale();
    if (!sale) return;
    if (code === '2') {
      this.reasonDescription.set('Anulación de factura');
      const full: Record<string, number> = {};
      for (const item of sale.items) {
        full[item.productId] = item.quantity - this.returnedQty(sale, item.productId);
      }
      this.returnQty.set(full);
    } else if (code === '1') {
      this.reasonDescription.set('Devolución parcial de mercancía');
    } else {
      this.reasonDescription.set('Rebaja o descuento aplicado');
    }
  }

  setQty(productId: string, value: string): void {
    const qty = Math.max(0, Number(value) || 0);
    this.returnQty.update((current) => ({ ...current, [productId]: qty }));
  }

  async submitVoid(): Promise<void> {
    const sale = this.modalSale();
    if (!sale) return;
    if (!navigator.onLine) {
      this.feedback.set('Se requiere conexión para transmitir la nota crédito a la DIAN.');
      return;
    }

    const items = sale.items
      .map((i) => ({ productId: i.productId, quantity: this.returnQty()[i.productId] ?? 0 }))
      .filter((i) => i.quantity > 0);

    if (items.length === 0) {
      this.feedback.set('Selecciona al menos una cantidad a devolver.');
      return;
    }

    this.busy.set(true);
    this.feedback.set('');
    try {
      const terminal = await this.sync.terminal();
      const note = await this.creditNoteService.create({
        id: uuidv7(),
        saleId: sale.id,
        terminalId: terminal?.id ?? sale.terminalId,
        reasonCode: this.reasonCode(),
        reasonDescription: this.reasonDescription().trim(),
        items,
      });
      this.lastNote.set(note);
      this.modalSale.set(null);
      await this.reload();
      await this.sync.pull();
    } catch (error) {
      this.feedback.set(error instanceof Error ? error.message : String(error));
    } finally {
      this.busy.set(false);
    }
  }

  dismissReceipt(): void {
    this.lastNote.set(null);
  }
}
