import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { db, readConfig } from '../../core/db/pos-offline.database';
import {
  money,
  round2,
  type CartLine,
  type PaymentMethod,
  type Product,
  type SecurityPolicy,
  type TerminalConfig,
} from '../../core/models/pos.models';
import { PosCheckoutService } from '../../core/services/pos-checkout.service';
import { PinAuthService } from '../../core/services/pin-auth.service';
import { SyncService } from '../../core/services/sync.service';
import { SupervisorPinModal } from '../../shared/components/supervisor-pin-modal/supervisor-pin-modal';
import { securityPolicyKey } from '../../core/db/pos-offline.database';

type PendingAction =
  | { kind: 'remove'; productId: string }
  | { kind: 'lineDiscount'; productId: string; percent: number }
  | { kind: 'cartDiscount'; percent: number }
  | { kind: 'checkout' };

@Component({
  selector: 'app-pos',
  templateUrl: './pos.html',
  styleUrl: './pos.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, SupervisorPinModal],
})
export class Pos implements OnInit {
  private readonly checkoutService = inject(PosCheckoutService);
  private readonly pinAuth = inject(PinAuthService);
  readonly sync = inject(SyncService);

  readonly products = signal<Product[]>([]);
  readonly query = signal('');
  readonly cart = signal<CartLine[]>([]);
  readonly payment = signal<PaymentMethod>('EFECTIVO');
  readonly terminal = signal<TerminalConfig | null>(null);
  readonly policy = signal<SecurityPolicy>({
    requirePinForDiscounts: true,
    maxCashierDiscountPercent: 0,
    requirePinForVoidItem: true,
    blindCashClose: true,
  });
  readonly feedback = signal('');
  readonly busy = signal(false);
  readonly money = money;

  readonly pinOpen = signal(false);
  readonly pinTitle = signal('Autorización de supervisor');
  readonly pinMessage = signal('Ingresa el PIN de supervisor para continuar.');
  readonly pinError = signal('');
  readonly pinBusy = signal(false);
  readonly authorizedBy = signal<string | null>(null);
  readonly authorizedName = signal<string | null>(null);
  private pendingAction: PendingAction | null = null;

  constructor() {
    effect(() => {
      if (this.sync.lastSyncAt()) {
        void this.reload();
      }
    });
  }

  readonly filtered = computed(() => {
    const query = this.query().trim().toLowerCase();
    const products = this.products();
    if (!query) {
      return products;
    }
    return products.filter(
      (product) =>
        product.name.toLowerCase().includes(query) ||
        product.sku.toLowerCase().includes(query) ||
        (product.barcode ?? '').toLowerCase().includes(query),
    );
  });

  readonly totals = computed(() => this.checkoutService.computeTotals(this.cart()));
  readonly subtotal = computed(() => this.totals().subtotal);
  readonly discountTotal = computed(() => this.totals().discountTotal);
  readonly taxTotal = computed(() => this.totals().taxTotal);
  readonly grandTotal = computed(() => this.totals().grandTotal);

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.products.set(await db.products.orderBy('name').toArray());
    this.terminal.set((await this.sync.terminalConfig()) ?? null);
    const p = await readConfig<SecurityPolicy>(securityPolicyKey);
    if (p) {
      this.policy.set({
        ...p,
        maxCashierDiscountPercent: Number(p.maxCashierDiscountPercent ?? 0),
      });
    }
  }

  onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  onPayment(event: Event): void {
    this.payment.set((event.target as HTMLSelectElement).value as PaymentMethod);
  }

  add(product: Product): void {
    const existing = this.cart().find((line) => line.productId === product.id);
    if (product.currentStock <= 0 || (existing && existing.quantity >= product.currentStock)) {
      this.feedback.set(`Sin stock disponible para ${product.name}`);
      return;
    }
    this.feedback.set('');
    this.cart.update((lines) => {
      if (existing) {
        return lines.map((line) =>
          line.productId === product.id ? { ...line, quantity: line.quantity + 1 } : line,
        );
      }
      const line: CartLine = {
        productId: product.id,
        name: product.name,
        sku: product.sku,
        quantity: 1,
        unitPrice: product.salePrice,
        taxRate: product.taxRate,
        discountPercent: 0,
        discountAmount: 0,
        currentStock: product.currentStock,
      };
      return [...lines, line];
    });
  }

  changeQuantity(productId: string, delta: number): void {
    this.cart.update((lines) =>
      lines.flatMap((line) => {
        if (line.productId !== productId) return [line];
        const next = line.quantity + delta;
        if (next <= 0) {
          return [line]; // no eliminar por delta; usar botón quitar
        }
        const product = this.products().find((p) => p.id === productId);
        if (product && next > product.currentStock) {
          this.feedback.set(`Sin stock disponible para ${line.name}`);
          return [line];
        }
        return [{ ...line, quantity: next }];
      }),
    );
  }

  requestRemove(productId: string): void {
    if (this.policy().requirePinForVoidItem) {
      this.pendingAction = { kind: 'remove', productId };
      this.openPin('Autorización para quitar ítem', 'Se requiere PIN de supervisor para retirar un producto del carrito.');
      return;
    }
    this.removeLine(productId);
  }

  private removeLine(productId: string): void {
    this.cart.update((lines) => lines.filter((l) => l.productId !== productId));
    this.feedback.set('Ítem retirado del carrito.');
  }

  onLineDiscountInput(productId: string, event: Event): void {
    const value = Math.max(0, Math.min(100, Number((event.target as HTMLInputElement).value) || 0));
    this.requestLineDiscount(productId, value);
  }

  requestLineDiscount(productId: string, percent: number): void {
    const policy = this.policy();
    const needsPin = policy.requirePinForDiscounts && percent > Number(policy.maxCashierDiscountPercent || 0);
    if (needsPin) {
      this.pendingAction = { kind: 'lineDiscount', productId, percent };
      this.openPin(
        'Autorización de descuento',
        `El descuento ${percent}% supera el máximo permitido (${policy.maxCashierDiscountPercent}%). Ingresa PIN de supervisor.`,
      );
      // revert visual by not applying yet
      return;
    }
    this.applyLineDiscount(productId, percent);
  }

  private applyLineDiscount(productId: string, percent: number): void {
    this.cart.update((lines) =>
      lines.map((l) => (l.productId === productId ? { ...l, discountPercent: percent, discountAmount: 0 } : l)),
    );
  }

  applyCartDiscount(event: Event): void {
    const percent = Math.max(0, Math.min(100, Number((event.target as HTMLInputElement).value) || 0));
    const policy = this.policy();
    const needsPin = policy.requirePinForDiscounts && percent > Number(policy.maxCashierDiscountPercent || 0);
    if (needsPin) {
      this.pendingAction = { kind: 'cartDiscount', percent };
      this.openPin(
        'Autorización de descuento total',
        `El descuento total ${percent}% requiere autorización de supervisor.`,
      );
      (event.target as HTMLInputElement).value = '0';
      return;
    }
    this.setCartDiscount(percent);
  }

  private setCartDiscount(percent: number): void {
    this.cart.update((lines) => lines.map((l) => ({ ...l, discountPercent: percent, discountAmount: 0 })));
  }

  private openPin(title: string, message: string): void {
    this.pinTitle.set(title);
    this.pinMessage.set(message);
    this.pinError.set('');
    this.pinBusy.set(false);
    this.pinOpen.set(true);
  }

  async onPinSubmit(pin: string): Promise<void> {
    this.pinBusy.set(true);
    this.pinError.set('');
    try {
      const auth = await this.pinAuth.authorize(pin);
      if (!auth) {
        this.pinError.set('PIN inválido. Verifica e intenta de nuevo.');
        return;
      }
      this.authorizedBy.set(auth.authorizedBy);
      this.authorizedName.set(auth.authorizedName);
      this.pinOpen.set(false);
      const action = this.pendingAction;
      this.pendingAction = null;
      if (!action) return;
      if (action.kind === 'remove') {
        this.removeLine(action.productId);
      } else if (action.kind === 'lineDiscount') {
        this.applyLineDiscount(action.productId, action.percent);
        this.feedback.set(`Descuento autorizado por ${auth.authorizedName}.`);
      } else if (action.kind === 'cartDiscount') {
        this.setCartDiscount(action.percent);
        this.feedback.set(`Descuento total autorizado por ${auth.authorizedName}.`);
      } else if (action.kind === 'checkout') {
        await this.doCheckout(auth.authorizedBy);
      }
    } finally {
      this.pinBusy.set(false);
    }
  }

  onPinCancel(): void {
    this.pinOpen.set(false);
    this.pinError.set('');
    this.pendingAction = null;
  }

  async cobrar(): Promise<void> {
    const terminal = this.terminal();
    if (!terminal) {
      this.feedback.set('Esperando la configuración de la terminal (conéctate una vez al inicio).');
      return;
    }
    if (this.cart().length === 0) return;

    const policy = this.policy();
    const totals = this.totals();
    const maxLineDiscount = this.cart().reduce((m, l) => Math.max(m, Number(l.discountPercent || 0)), 0);
    const needsPin = policy.requirePinForDiscounts
      && (totals.discountTotal > 0 || maxLineDiscount > Number(policy.maxCashierDiscountPercent || 0))
      && maxLineDiscount > Number(policy.maxCashierDiscountPercent || 0);
    if (needsPin && !this.authorizedBy()) {
      this.pendingAction = { kind: 'checkout' };
      this.openPin('Autorización para cobrar', 'El descuento aplicado requiere autorización de supervisor para finalizar la venta.');
      return;
    }

    await this.doCheckout(this.authorizedBy());
  }

  private async doCheckout(authorizedBy: string | null): Promise<void> {
    const terminal = this.terminal();
    if (!terminal) return;
    this.busy.set(true);
    this.feedback.set('');
    try {
      const sale = await this.checkoutService.checkout(this.cart(), this.payment(), terminal, {
        authorizedBy,
      });
      this.cart.set([]);
      this.authorizedBy.set(null);
      this.authorizedName.set(null);
      this.feedback.set(
        `Venta ${sale.fullDocumentNumber} registrada${this.sync.online() ? '' : ' sin conexión'}. ` +
          `Total ${this.money(sale.grandTotal)}.`,
      );
      await this.reload();
      await this.sync.synchronize();
    } catch (error) {
      this.feedback.set(error instanceof Error ? error.message : String(error));
    } finally {
      this.busy.set(false);
    }
  }
}