import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ReportService } from '../../core/services/report.service';
import type { SaleSummaryResponse, TaxSummaryResponse, TopProductsResponse } from '../../core/models/pos.models';

type Tab = 'sales' | 'top' | 'tax';
type RangePreset = 'today' | 'week' | 'month' | 'custom';

@Component({
  selector: 'app-reports',
  templateUrl: './reports.html',
  styleUrl: './reports.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DecimalPipe],
})
export class Reports implements OnInit {
  private readonly reportService = inject(ReportService);

  readonly tab = signal<Tab>('sales');
  readonly preset = signal<RangePreset>('month');
  readonly from = signal(this.firstDayOfMonth());
  readonly to = signal(this.today());

  readonly sales = signal<SaleSummaryResponse | null>(null);
  readonly top = signal<TopProductsResponse | null>(null);
  readonly tax = signal<TaxSummaryResponse | null>(null);

  readonly loading = signal(false);
  readonly exporting = signal(false);
  readonly error = signal('');

  readonly maxTopRevenue = computed(() => {
    const items = this.top()?.items ?? [];
    return items.reduce((m, i) => Math.max(m, Number(i.revenue || 0)), 0) || 1;
  });

  async ngOnInit(): Promise<void> {
    await this.refresh();
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
  }

  setPreset(preset: RangePreset): void {
    this.preset.set(preset);
    const today = this.today();
    if (preset === 'today') {
      this.from.set(today); this.to.set(today);
    } else if (preset === 'week') {
      const d = new Date();
      const day = d.getDay() || 7;
      d.setDate(d.getDate() - day + 1);
      this.from.set(this.fmt(d)); this.to.set(today);
    } else if (preset === 'month') {
      this.from.set(this.firstDayOfMonth()); this.to.set(today);
    }
  }

  onFrom(event: Event): void { this.from.set((event.target as HTMLInputElement).value); this.preset.set('custom'); }
  onTo(event: Event): void { this.to.set((event.target as HTMLInputElement).value); this.preset.set('custom'); }

  async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const from = this.from();
      const to = this.to();
      const [sales, top, tax] = await Promise.all([
        this.reportService.salesSummary(from, to),
        this.reportService.topProducts(from, to, 20),
        this.reportService.taxSummary(from, to),
      ]);
      this.sales.set(sales);
      this.top.set(top);
      this.tax.set(tax);
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible cargar los reportes');
    } finally {
      this.loading.set(false);
    }
  }

  async exportExcel(): Promise<void> {
    this.exporting.set(true);
    this.error.set('');
    try {
      await this.reportService.downloadTaxSummaryExcel(this.from(), this.to());
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible exportar el informe tributario');
    } finally {
      this.exporting.set(false);
    }
  }

  pct(revenue: number): number {
    return Math.max(2, Math.round((Number(revenue || 0) / this.maxTopRevenue()) * 100));
  }

  private today(): string { return this.fmt(new Date()); }
  private firstDayOfMonth(): string {
    const d = new Date(); d.setDate(1); return this.fmt(d);
  }
  private fmt(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}