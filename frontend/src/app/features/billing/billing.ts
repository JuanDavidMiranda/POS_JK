import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BillingApiService, type BillingOverview, type CheckoutIntentResponse } from '../../core/services/billing-api.service';

@Component({
  selector: 'app-billing',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './billing.html',
  styleUrl: './billing.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Billing implements OnInit {
  private readonly api = inject(BillingApiService);

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly overview = signal<BillingOverview | null>(null);
  readonly cycle = signal<'MONTHLY' | 'ANNUAL'>('MONTHLY');
  readonly checkout = signal<CheckoutIntentResponse | null>(null);

  readonly subscription = computed(() => this.overview()?.subscription ?? null);
  readonly plans = computed(() => (this.overview()?.plans ?? []).filter(p => p.code !== 'TRIAL'));
  readonly packages = computed(() => this.overview()?.folioPackages ?? []);
  readonly txs = computed(() => this.overview()?.recentTransactions ?? []);
  readonly sandbox = computed(() => this.overview()?.wompiSandbox ?? true);

  readonly folioPercent = computed(() => {
    const s = this.subscription();
    if (!s || !s.electronicFoliosLimit) return 0;
    return Math.min(100, Math.round((s.electronicFoliosUsed / s.electronicFoliosLimit) * 100));
  });

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.overview.set(await this.api.overview());
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible cargar la información de facturación');
    } finally {
      this.loading.set(false);
    }
  }

  planPrice(code: string): number {
    const p = this.plans().find(x => x.code === code);
    if (!p) return 0;
    return this.cycle() === 'MONTHLY' ? p.monthlyPriceCop : p.annualPriceCop;
  }

  async buyPlan(code: string): Promise<void> {
    const paymentType = this.cycle() === 'MONTHLY' ? 'SUBSCRIPTION_MONTHLY' : 'SUBSCRIPTION_ANNUAL';
    await this.startCheckout(paymentType, code);
  }

  async buyFolios(code: string): Promise<void> {
    await this.startCheckout('FOLIO_TOPUP', code);
  }

  private async startCheckout(paymentType: string, itemCode: string): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    try {
      const intent = await this.api.checkoutIntent(paymentType, itemCode);
      this.checkout.set(intent);
      if (intent.sandbox) {
        this.message.set('Checkout creado en modo SANDBOX. Usa el simulador para aprobar o rechazar el pago.');
      } else {
        this.openWompiWidget(intent);
      }
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible iniciar el pago');
    } finally {
      this.busy.set(false);
    }
  }

  openWompiWidget(intent?: CheckoutIntentResponse): void {
    const c = intent || this.checkout();
    if (!c) return;
    const w = window as any;
    if (typeof w.WidgetCheckout === 'function') {
      try {
        const widget = new w.WidgetCheckout({
          currency: c.currency,
          amountInCents: c.amountInCents,
          reference: c.reference,
          publicKey: c.publicKey,
          redirectUrl: c.redirectUrl,
          signature: { integrity: c.integritySignature },
        });
        widget.open(() => { void this.load(); });
        return;
      } catch {
        // fallback
      }
    }
    this.message.set('Widget de Wompi no disponible en este entorno. Copia la referencia o usa el simulador SANDBOX si está activo.');
  }

  async simulate(approve: boolean): Promise<void> {
    const c = this.checkout();
    if (!c) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.simulateSandbox(c.reference, approve);
      this.message.set(approve ? 'Pago simulado y aprobado. Suscripción/folios actualizados.' : 'Pago simulado como rechazado.');
      this.checkout.set(null);
      await this.load();
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible simular el pago');
    } finally {
      this.busy.set(false);
    }
  }

  cancelCheckout(): void { this.checkout.set(null); }

  statusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'ACTIVE': return 'bg-emerald-100 text-emerald-800';
      case 'TRIAL': return 'bg-blue-100 text-blue-800';
      case 'PAST_DUE': return 'bg-amber-100 text-amber-800';
      case 'SUSPENDED': return 'bg-rose-100 text-rose-800';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  txStatusClass(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'APPROVED': return 'text-emerald-700';
      case 'PENDING': return 'text-amber-700';
      case 'DECLINED':
      case 'ERROR':
      case 'VOIDED': return 'text-rose-700';
      default: return 'text-slate-600';
    }
  }
}
