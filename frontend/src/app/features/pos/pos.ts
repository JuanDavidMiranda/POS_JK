import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { db } from '../../core/db/pos-offline.database';
import {
  money,
  round2,
  type CartLine,
  type PaymentMethod,
  type Product,
  type TerminalConfig,
} from '../../core/models/pos.models';
import { PosCheckoutService } from '../../core/services/pos-checkout.service';
import { SyncService } from '../../core/services/sync.service';

@Component({
  selector: 'app-pos',
  templateUrl: './pos.html',
  styleUrl: './pos.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pos implements OnInit {
  private readonly checkoutService = inject(PosCheckoutService);
  readonly sync = inject(SyncService);

  readonly products = signal<Product[]>([]);
  readonly query = signal('');
  readonly cart = signal<CartLine[]>([]);
  readonly payment = signal<PaymentMethod>('EFECTIVO');
  readonly terminal = signal<TerminalConfig | null>(null);
  readonly feedback = signal('');
  readonly busy = signal(false);
  readonly money = money;

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
        (product.barcode ?? '').includes(query),
    );
  });

  readonly subtotal = computed(() => round2(this.cart().reduce((sum, line) => sum + line.lineSubtotal, 0)));
  readonly taxTotal = computed(() => round2(this.cart().reduce((sum, line) => sum + line.lineTax, 0)));
  readonly grandTotal = computed(() => round2(this.subtotal() + this.taxTotal()));

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.products.set(await db.products.orderBy('name').toArray());
    this.terminal.set((await this.sync.terminal()) ?? null);
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
          line.productId === product.id ? this.withQuantity(line, line.quantity + 1) : line,
        );
      }
      const lineSubtotal = round2(product.salePrice);
      const lineTax = round2(lineSubtotal * product.taxRate);
      const line: CartLine = {
        productId: product.id,
        name: product.name,
        sku: product.sku,
        quantity: 1,
        unitPrice: product.salePrice,
        taxRate: product.taxRate,
        taxType: product.taxType,
        lineSubtotal,
        lineTax,
        lineTotal: round2(lineSubtotal + lineTax),
      };
      return [...lines, line];
    });
  }

  changeQuantity(productId: string, delta: number): void {
    this.cart.update((lines) =>
      lines.flatMap((line) => {
        if (line.productId !== productId) {
          return [line];
        }
        const next = line.quantity + delta;
        if (next <= 0) {
          return [];
        }
        const product = this.products().find((candidate) => candidate.id === productId);
        if (product && next > product.currentStock) {
          this.feedback.set(`Sin stock disponible para ${line.name}`);
          return [line];
        }
        return [this.withQuantity(line, next)];
      }),
    );
  }

  async cobrar(): Promise<void> {
    const terminal = this.terminal();
    if (!terminal) {
      this.feedback.set('Esperando la configuración de la terminal (conéctate una vez al inicio).');
      return;
    }

    this.busy.set(true);
    this.feedback.set('');
    try {
      const sale = await this.checkoutService.checkout(this.cart(), this.payment(), terminal);
      this.cart.set([]);
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

  private withQuantity(line: CartLine, quantity: number): CartLine {
    const lineSubtotal = round2(line.unitPrice * quantity);
    const lineTax = round2(lineSubtotal * line.taxRate);
    return {
      ...line,
      quantity,
      lineSubtotal,
      lineTax,
      lineTotal: round2(lineSubtotal + lineTax),
    };
  }
}
