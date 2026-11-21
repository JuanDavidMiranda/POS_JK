import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { AccountingApiService, type AccountingConfig, type AccountingPreview } from '../../core/services/accounting-api.service';

@Component({
  selector: 'app-accounting',
  imports: [FormsModule, CurrencyPipe],
  templateUrl: './accounting.html',
  styleUrl: './accounting.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accounting implements OnInit {
  private readonly api = inject(AccountingApiService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly exporting = signal(false);
  readonly previewing = signal(false);
  readonly error = signal('');
  readonly message = signal('');

  readonly config = signal<AccountingConfig>({
    targetSoftware: 'GENERIC_PUC',
    salesIncomeAccount: '413505',
    vatGeneratedAccount: '240805',
    incGeneratedAccount: '249505',
    cashAccount: '110505',
    bankTransferAccount: '111005',
    returnsAccount: '417505',
  });

  readonly from = signal(this.firstDayOfMonth());
  readonly to = signal(this.today());
  readonly format = signal('GENERIC_PUC');
  readonly preview = signal<AccountingPreview | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.config.set(await this.api.getConfig());
      this.format.set(this.config().targetSoftware || 'GENERIC_PUC');
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible cargar la configuración contable');
    } finally {
      this.loading.set(false);
    }
  }

  setField<K extends keyof AccountingConfig>(key: K, value: AccountingConfig[K]) {
    this.config.update(c => ({ ...c, [key]: value }));
  }

  async saveConfig(): Promise<void> {
    this.saving.set(true); this.error.set(''); this.message.set('');
    try {
      const saved = await this.api.updateConfig(this.config());
      this.config.set(saved);
      this.message.set('Configuración contable guardada.');
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible guardar la configuración');
    } finally { this.saving.set(false); }
  }

  async loadPreview(): Promise<void> {
    this.previewing.set(true); this.error.set('');
    try {
      this.preview.set(await this.api.preview(this.from(), this.to(), this.format()));
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible generar la vista previa');
      this.preview.set(null);
    } finally { this.previewing.set(false); }
  }

  async download(): Promise<void> {
    this.exporting.set(true); this.error.set('');
    try {
      await this.api.export(this.from(), this.to(), this.format());
      this.message.set('Archivo de exportación generado.');
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible exportar el archivo');
    } finally { this.exporting.set(false); }
  }

  private today(): string { return new Date().toISOString().slice(0, 10); }
  private firstDayOfMonth(): string { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0,10); }
}
