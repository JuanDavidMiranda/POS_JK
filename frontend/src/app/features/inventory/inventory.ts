import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { db } from '../../core/db/pos-offline.database';
import { InventoryService } from '../../core/services/inventory.service';
import { SyncService } from '../../core/services/sync.service';
import {
  type InventoryMovementRecord,
  type MovementType,
  type Product,
} from '../../core/models/pos.models';

@Component({
  selector: 'app-inventory',
  templateUrl: './inventory.html',
  styleUrl: './inventory.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DecimalPipe],
})
export class Inventory implements OnInit {
  private readonly inventoryService = inject(InventoryService);
  readonly sync = inject(SyncService);

  readonly products = signal<Product[]>([]);
  readonly showLowStockOnly = signal(false);
  readonly lowStockIds = signal<Set<string> | null>(null);
  readonly selected = signal<Product | null>(null);
  readonly kardex = signal<InventoryMovementRecord[]>([]);
  readonly loadingKardex = signal(false);

  readonly movementModalOpen = signal(false);
  readonly movementProduct = signal<Product | null>(null);
  readonly movementType = signal<MovementType>('PURCHASE_IN');
  readonly movementQuantity = signal<number>(1);
  readonly movementNotes = signal('');
  readonly feedback = signal('');
  readonly busy = signal(false);

  readonly filtered = computed(() => {
    const list = this.products();
    if (!this.showLowStockOnly()) {
      return list;
    }
    const ids = this.lowStockIds();
    if (ids) {
      return list.filter((p) => ids.has(p.id));
    }
    return list.filter((p) => Number(p.currentStock) <= Number(p.minStock ?? 0));
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
    this.products.set(await db.products.orderBy('name').toArray());
    if (this.showLowStockOnly()) {
      await this.refreshLowStockIds();
    }
  }

  async toggleLowStock(): Promise<void> {
    const next = !this.showLowStockOnly();
    this.showLowStockOnly.set(next);
    if (next) {
      await this.refreshLowStockIds();
    } else {
      this.lowStockIds.set(null);
    }
  }

  private async refreshLowStockIds(): Promise<void> {
    try {
      const low = await this.inventoryService.lowStock();
      this.lowStockIds.set(new Set(low.map((p) => p.id)));
    } catch {
      this.lowStockIds.set(null);
    }
  }

  openMovement(product?: Product): void {
    this.movementProduct.set(product ?? this.products()[0] ?? null);
    this.movementType.set('PURCHASE_IN');
    this.movementQuantity.set(1);
    this.movementNotes.set('');
    this.feedback.set('');
    this.movementModalOpen.set(true);
  }

  closeMovement(): void {
    this.movementModalOpen.set(false);
  }

  openKardex(product: Product): void {
    this.selected.set(product);
    void this.loadKardex(product.id);
  }

  closeKardex(): void {
    this.selected.set(null);
    this.kardex.set([]);
  }

  async loadKardex(productId: string): Promise<void> {
    this.loadingKardex.set(true);
    try {
      const page = await this.inventoryService.kardex(productId, 0, 50);
      this.kardex.set(page.content);
    } finally {
      this.loadingKardex.set(false);
    }
  }

  async submitMovement(): Promise<void> {
    const product = this.movementProduct();
    if (!product) {
      this.feedback.set('Selecciona un producto');
      return;
    }
    if (this.movementQuantity() <= 0) {
      this.feedback.set('La cantidad debe ser mayor a cero');
      return;
    }
    if (!this.movementNotes().trim()) {
      this.feedback.set('La observación es obligatoria');
      return;
    }

    this.busy.set(true);
    this.feedback.set('');
    try {
      await this.inventoryService.registerMovement({
        productId: product.id,
        movementType: this.movementType(),
        quantity: this.movementQuantity(),
        notes: this.movementNotes().trim(),
      });
      await this.reload();
      await this.sync.flushPendingInventoryMovements();
      this.closeMovement();
      if (this.selected()?.id === product.id) {
        await this.loadKardex(product.id);
      }
    } catch (error) {
      this.feedback.set(error instanceof Error ? error.message : String(error));
    } finally {
      this.busy.set(false);
    }
  }

  setMovementProduct(id: string): void {
    this.movementProduct.set(this.products().find((p) => p.id === id) ?? null);
  }

  setMovementType(value: string): void {
    this.movementType.set(value as MovementType);
  }

  setMovementQuantity(value: string): void {
    const parsed = Number(value);
    this.movementQuantity.set(Number.isFinite(parsed) ? parsed : 0);
  }

  setMovementNotes(value: string): void {
    this.movementNotes.set(value);
  }

  movementLabel(type: MovementType): string {
    const labels: Record<MovementType, string> = {
      INITIAL_BALANCE: 'Saldo inicial',
      PURCHASE_IN: 'Compra (entrada)',
      SALE_OUT: 'Venta (salida)',
      CREDIT_NOTE_IN: 'Nota crédito (entrada)',
      ADJUSTMENT_IN: 'Ajuste (entrada)',
      ADJUSTMENT_OUT: 'Ajuste (salida)',
    };
    return labels[type] ?? type;
  }

  movementSign(type: MovementType): string {
    return type === 'SALE_OUT' || type === 'ADJUSTMENT_OUT' ? '-' : '+';
  }

  isLow(product: Product): boolean {
    return Number(product.currentStock) <= Number(product.minStock ?? 0);
  }

  stockClass(product: Product): string {
    const stock = Number(product.currentStock);
    const min = Number(product.minStock ?? 0);
    if (stock <= 0) {
      return 'bg-rose-100 text-rose-700';
    }
    if (stock <= min) {
      return 'bg-amber-100 text-amber-700';
    }
    return 'bg-emerald-100 text-emerald-700';
  }

  formatDate(iso: string): string {
    return new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));
  }
}
