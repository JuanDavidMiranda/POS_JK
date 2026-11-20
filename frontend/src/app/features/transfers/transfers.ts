import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BranchService } from '../../core/services/branch.service';
import { TransferService, type CreateTransferItemRequest } from '../../core/services/transfer.service';
import type { Branch, InventoryTransfer, Product, TerminalInfo, TransferStatus } from '../../core/models/pos.models';

interface OriginProduct extends Product { originStock: number; }

@Component({
  selector: 'app-transfers',
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './transfers.html',
  styleUrl: './transfers.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Transfers implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly branchService = inject(BranchService);
  private readonly transferService = inject(TransferService);

  readonly branches = signal<Branch[]>([]);
  readonly terminals = signal<TerminalInfo[]>([]);
  readonly transfers = signal<InventoryTransfer[]>([]);
  readonly loading = signal(false);
  readonly creating = signal(false);
  readonly actionBusyId = signal<string | null>(null);
  readonly error = signal('');
  readonly feedback = signal('');

  readonly statusFilter = signal<TransferStatus | ''>('');
  readonly listSource = signal<string>('');
  readonly listDest = signal<string>('');

  readonly sourceBranchId = signal<string>('');
  readonly destinationBranchId = signal<string>('');
  readonly notes = signal<string>('');
  readonly originProducts = signal<OriginProduct[]>([]);
  readonly originLoading = signal(false);
  readonly productSearch = signal<string>('');
  readonly draftItems = signal<CreateTransferItemRequest[]>([]);
  readonly qtyByProduct = signal<Record<string, number>>({});

  readonly filteredOriginProducts = computed(() => {
    const q = this.productSearch().trim().toLowerCase();
    const list = this.originProducts();
    if (!q) return list;
    return list.filter((p) => (p.name || '').toLowerCase().includes(q) || (p.sku || '').toLowerCase().includes(q) || (p.barcode || '').toLowerCase().includes(q));
  });

  async ngOnInit(): Promise<void> {
    await this.loadBase();
    await this.loadTransfers();
  }

  async loadBase(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [branches, terminals] = await Promise.all([this.branchService.listBranches(), this.branchService.listTerminals()]);
      this.branches.set(branches.filter((b) => b.active));
      this.terminals.set(terminals.filter((t) => t.active));
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible cargar sedes'));
    } finally {
      this.loading.set(false);
    }
  }

  async loadTransfers(): Promise<void> {
    try {
      const page = await this.transferService.list({
        status: this.statusFilter() || null,
        sourceBranchId: this.listSource() || null,
        destinationBranchId: this.listDest() || null,
        page: 0, size: 50,
      });
      this.transfers.set(page.content ?? []);
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible cargar los traslados'));
    }
  }

  async onSourceChange(id: string): Promise<void> {
    this.sourceBranchId.set(id);
    this.draftItems.set([]);
    this.qtyByProduct.set({});
    await this.loadOriginProducts(id);
  }

  onDestChange(id: string): void { this.destinationBranchId.set(id); }

  private async loadOriginProducts(branchId: string): Promise<void> {
    if (!branchId) { this.originProducts.set([]); return; }
    const terminal = this.terminals().find((t) => t.branchId === branchId) ?? this.terminals().find((t) => t.branchId === branchId);
    if (!terminal) { this.originProducts.set([]); this.feedback.set('La sede origen no tiene cajas activas para consultar stock.'); return; }
    this.originLoading.set(true);
    try {
      const pull = await firstValueFrom(this.http.get<{ products: Product[] }>(`${environment.apiBaseUrl}/api/v1/sync/pull`, { params: { terminalId: terminal.id } }));
      const products = (pull.products ?? []).map((p) => ({ ...p, originStock: Number(p.currentStock ?? 0) }));
      this.originProducts.set(products.sort((a, b) => (a.name || '').localeCompare(b.name || '')));
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible cargar el stock de la sede origen'));
      this.originProducts.set([]);
    } finally {
      this.originLoading.set(false);
    }
  }

  setQty(productId: string, value: string): void {
    const n = Number(value);
    this.qtyByProduct.update((m) => ({ ...m, [productId]: Number.isFinite(n) ? n : 0 }));
  }

  addItem(p: OriginProduct): void {
    const qty = Number(this.qtyByProduct()[p.id] || 0);
    if (!(qty > 0)) { this.error.set('Ingresa una cantidad mayor a cero'); return; }
    if (qty > p.originStock) { this.error.set(`Stock insuficiente en origen para ${p.name} (disponible: ${p.originStock})`); return; }
    this.error.set('');
    this.draftItems.update((items) => {
      const idx = items.findIndex((i) => i.productId === p.id);
      if (idx >= 0) { const copy = [...items]; copy[idx] = { productId: p.id, quantity: qty }; return copy; }
      return [...items, { productId: p.id, quantity: qty }];
    });
  }

  removeItem(productId: string): void {
    this.draftItems.update((items) => items.filter((i) => i.productId !== productId));
  }

  draftQty(productId: string): number {
    return this.draftItems().find((i) => i.productId === productId)?.quantity ?? 0;
  }

  async createTransfer(): Promise<void> {
    const source = this.sourceBranchId();
    const dest = this.destinationBranchId();
    if (!source || !dest) { this.error.set('Selecciona sede origen y destino'); return; }
    if (source === dest) { this.error.set('La sede origen y destino deben ser diferentes'); return; }
    const items = this.draftItems();
    if (!items.length) { this.error.set('Agrega al menos un producto al traslado'); return; }
    this.creating.set(true); this.error.set(''); this.feedback.set('');
    try {
      await this.transferService.create({ sourceBranchId: source, destinationBranchId: dest, notes: this.notes().trim() || null, items });
      this.feedback.set('Traslado creado y despachado (EN TRÁNSITO). El stock fue descontado en la sede origen.');
      this.draftItems.set([]); this.qtyByProduct.set({}); this.notes.set('');
      await this.loadOriginProducts(source);
      await this.loadTransfers();
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible crear el traslado'));
    } finally { this.creating.set(false); }
  }

  async receive(t: InventoryTransfer): Promise<void> {
    if (!confirm(`¿Confirmar recepción del traslado ${t.transferNumber}? Se sumará el stock en la sede destino.`)) return;
    this.actionBusyId.set(t.id); this.error.set(''); this.feedback.set('');
    try {
      await this.transferService.receive(t.id);
      this.feedback.set(`Traslado ${t.transferNumber} recibido (COMPLETADO).`);
      await this.loadTransfers();
      if (this.sourceBranchId()) await this.loadOriginProducts(this.sourceBranchId());
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible confirmar la recepción'));
    } finally { this.actionBusyId.set(null); }
  }

  async cancel(t: InventoryTransfer): Promise<void> {
    const reason = prompt('Motivo de cancelación (opcional):') ?? '';
    if (reason === null) return;
    this.actionBusyId.set(t.id); this.error.set(''); this.feedback.set('');
    try {
      await this.transferService.cancel(t.id, reason || null);
      this.feedback.set(`Traslado ${t.transferNumber} cancelado. Stock revertido en origen.`);
      await this.loadTransfers();
      if (this.sourceBranchId()) await this.loadOriginProducts(this.sourceBranchId());
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible cancelar el traslado'));
    } finally { this.actionBusyId.set(null); }
  }

  statusClass(status: TransferStatus | string): string {
    switch (status) {
      case 'COMPLETED': return 'bg-emerald-100 text-emerald-800';
      case 'IN_TRANSIT': return 'bg-amber-100 text-amber-800';
      case 'CANCELLED': return 'bg-rose-100 text-rose-800';
      case 'DRAFT': return 'bg-slate-100 text-slate-700';
      default: return 'bg-slate-100 text-slate-700';
    }
  }
  statusLabel(status: TransferStatus | string): string {
    switch (status) {
      case 'COMPLETED': return 'Completado';
      case 'IN_TRANSIT': return 'En tránsito';
      case 'CANCELLED': return 'Cancelado';
      case 'DRAFT': return 'Borrador';
      default: return String(status);
    }
  }

  branchName(id: string): string { return this.branches().find((b) => b.id === id)?.name ?? id; }

  private msg(e: any, fallback: string): string {
    return e?.error?.message || e?.error?.detail || e?.message || fallback;
  }
}
