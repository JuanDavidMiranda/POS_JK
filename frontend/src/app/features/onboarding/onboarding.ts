import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { OnboardingApiService } from '../../core/services/onboarding-api.service';
import type { OnboardingStatusResponse } from '../../core/models/auth.models';

@Component({
  selector: 'app-onboarding',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
<div class="min-h-screen bg-slate-100">
  <header class="border-b border-slate-200 bg-white">
    <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
      <div class="flex items-center gap-2 font-bold text-slate-800">
        <span class="rounded-lg bg-emerald-600 px-2 py-1 text-white">POS</span>
        <span>Configuración inicial</span>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span class="text-slate-500">{{ tenantName() }}</span>
        <button type="button" (click)="completeLater()" class="rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100">Completar después</button>
        <button type="button" (click)="logout()" class="text-slate-500 hover:text-slate-800">Salir</button>
      </div>
    </div>
  </header>

  <main class="mx-auto max-w-5xl px-4 py-8">
    @if (loading()) {
      <div class="rounded-xl bg-white p-8 text-center text-slate-500 shadow-sm">Cargando estado de onboarding…</div>
    } @else {
      <div class="mb-6 flex items-center justify-center gap-2">
        @for (s of [1,2,3]; track s) {
          <div class="flex items-center gap-2">
            <div class="flex h-8 w-8 items-center justify-center rounded-full text-sm font-semibold"
                 [class.bg-emerald-600]="currentStep() >= s" [class.text-white]="currentStep() >= s"
                 [class.bg-slate-200]="currentStep() < s" [class.text-slate-600]="currentStep() < s">
              {{ done() || currentStep() > s ? '✓' : s }}
            </div>
            <span class="text-sm" [class.font-semibold]="currentStep()===s" [class.text-slate-800]="currentStep()>=s" [class.text-slate-400]="currentStep()<s">
              {{ s===1 ? 'Identidad fiscal' : s===2 ? 'Resolución DIAN' : 'Catálogo' }}
            </span>
            @if (s < 3) { <div class="mx-2 h-px w-10 bg-slate-300"></div> }
          </div>
        }
      </div>

      @if (message()) { <div class="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{{ message() }}</div> }
      @if (error()) { <div class="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error() }}</div> }

      @if (done()) {
        <div class="rounded-xl bg-white p-8 text-center shadow-sm">
          <div class="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-2xl text-emerald-700">✓</div>
          <h2 class="text-xl font-bold text-slate-800 mb-2">¡Tu negocio está listo!</h2>
          <p class="text-slate-600 mb-6">Onboarding completado. Ya puedes empezar a vender.</p>
          <a routerLink="/pos" class="inline-flex rounded-lg bg-emerald-600 px-6 py-3 font-semibold text-white hover:bg-emerald-700">¡Ir a vender al POS!</a>
        </div>
      } @else if (currentStep() === 1) {
        <div class="rounded-xl bg-white p-6 shadow-sm">
          <h2 class="text-lg font-semibold text-slate-800 mb-1">Paso 1: Identidad fiscal y del comercio</h2>
          <p class="text-sm text-slate-500 mb-5">Configura la información que aparecerá en tus facturas y documentos.</p>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Régimen tributario *</span>
              <select [(ngModel)]="fiscal.taxRegime" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5">
                <option value="NO_RESPONSABLE_IVA">No responsable de IVA</option>
                <option value="RESPONSABLE_IVA">Responsable de IVA</option>
                <option value="REGIMEN_SIMPLE">Régimen Simple</option>
              </select>
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Responsabilidades fiscales *</span>
              <input [(ngModel)]="fiscal.fiscalResponsibilities" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="R-99-PN" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Ciudad</span>
              <input [(ngModel)]="fiscal.city" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Teléfono</span>
              <input [(ngModel)]="fiscal.phone" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block md:col-span-2">
              <span class="text-sm font-medium text-slate-700">Dirección</span>
              <input [(ngModel)]="fiscal.address" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block md:col-span-2">
              <span class="text-sm font-medium text-slate-700">Mensaje pie de recibo</span>
              <input [(ngModel)]="fiscal.receiptFooterMessage" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="Gracias por su compra" />
            </label>
          </div>
          <div class="mt-6 flex justify-end">
            <button type="button" (click)="saveFiscal()" [disabled]="saving()" class="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
              {{ saving() ? 'Guardando…' : 'Guardar y continuar' }}
            </button>
          </div>
        </div>
      } @else if (currentStep() === 2) {
        <div class="rounded-xl bg-white p-6 shadow-sm">
          <h2 class="text-lg font-semibold text-slate-800 mb-1">Paso 2: Resolución DIAN (Caja principal)</h2>
          <p class="text-sm text-slate-500 mb-5">Configura la numeración autorizada para facturación electrónica/POS. Puedes validarla antes de continuar.</p>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Prefijo *</span>
              <input [(ngModel)]="dian.dianPrefix" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="POS1" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Número de resolución</span>
              <input [(ngModel)]="dian.dianResolutionNumber" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Fecha resolución</span>
              <input [(ngModel)]="dian.dianResolutionDate" type="date" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Llave técnica</span>
              <input [(ngModel)]="dian.dianTechnicalKey" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Vigencia desde</span>
              <input [(ngModel)]="dian.dianValidFrom" type="date" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Vigencia hasta</span>
              <input [(ngModel)]="dian.dianValidTo" type="date" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Rango desde *</span>
              <input [(ngModel)]="dian.rangeFrom" type="number" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">Rango hasta *</span>
              <input [(ngModel)]="dian.rangeTo" type="number" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
            </label>
          </div>
          @if (verificationMsg()) {
            <div class="mt-4 rounded-lg border px-4 py-3 text-sm"
                 [class.border-emerald-200]="verificationOk()" [class.bg-emerald-50]="verificationOk()" [class.text-emerald-800]="verificationOk()"
                 [class.border-amber-200]="!verificationOk()" [class.bg-amber-50]="!verificationOk()" [class.text-amber-800]="!verificationOk()">
              <strong>{{ verificationMode() }}:</strong> {{ verificationMsg() }}
            </div>
          }
          <div class="mt-6 flex flex-wrap justify-between gap-3">
            <button type="button" (click)="saveDian(true)" [disabled]="saving()" class="rounded-lg border border-emerald-600 px-4 py-2.5 font-semibold text-emerald-700 hover:bg-emerald-50 disabled:opacity-60">
              {{ saving() ? 'Verificando…' : 'Verificar configuración' }}
            </button>
            <button type="button" (click)="saveDian(false)" [disabled]="saving()" class="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
              {{ saving() ? 'Guardando…' : 'Guardar y continuar' }}
            </button>
          </div>
        </div>
      } @else if (currentStep() === 3) {
        <div class="rounded-xl bg-white p-6 shadow-sm">
          <h2 class="text-lg font-semibold text-slate-800 mb-1">Paso 3: Catálogo inicial</h2>
          <p class="text-sm text-slate-500 mb-5">Carga productos de prueba según tu nicho, importa tu propio Excel o salta este paso para configurarlo después.</p>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
            <label class="block md:col-span-2">
              <span class="text-sm font-medium text-slate-700">Nicho / plantilla rápida</span>
              <select [(ngModel)]="niche" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5">
                <option value="MINIMERCADO">Minimercado / Tienda</option>
                <option value="DROGUERIA">Droguería</option>
                <option value="FERRETERIA">Ferretería</option>
                <option value="MISCELANEA">Miscelánea / Papelería</option>
              </select>
              <span class="text-xs text-slate-500">Se crearán 15 productos de ejemplo con inventario inicial en la sede principal y su kardex.</span>
            </label>
          </div>

          <div class="flex flex-wrap gap-3">
            <button type="button" (click)="seed('SEED')" [disabled]="saving()" class="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
              {{ saving() ? 'Cargando…' : 'Cargar catálogo de prueba' }}
            </button>
            <a routerLink="/cargue-masivo" (click)="goToImport()" class="rounded-lg border border-slate-300 px-5 py-2.5 font-semibold text-slate-700 hover:bg-slate-50">
              Ir a cargue masivo (Excel)
            </a>
            <button type="button" (click)="seed('SKIP')" [disabled]="saving()" class="rounded-lg border border-slate-300 px-5 py-2.5 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60">
              Omitir por ahora
            </button>
          </div>
          <p class="mt-4 text-xs text-slate-500">Si eliges "Ir a cargue masivo", se omitirá la siembra automática y podrás importar tu archivo desde el módulo de Cargue Masivo.</p>
        </div>
      }
    }
  </main>
</div>
  `
})
export class OnboardingComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly api = inject(OnboardingApiService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly status = signal<OnboardingStatusResponse | null>(null);

  readonly tenantName = computed(() => this.status()?.tenant?.tradeName || this.status()?.tenant?.businessName || this.auth.tenant()?.tradeName || 'Tu empresa');
  readonly currentStep = computed(() => this.status()?.onboardingStep ?? 1);
  readonly done = computed(() => !!this.status()?.onboardingCompleted);

  fiscal = { taxRegime: 'NO_RESPONSABLE_IVA', fiscalResponsibilities: 'R-99-PN', city: '', address: '', phone: '', receiptFooterMessage: 'Gracias por su compra' };
  dian = { dianPrefix: 'POS1', dianResolutionNumber: '', dianResolutionDate: '', dianValidFrom: '', dianValidTo: '', rangeFrom: 1, rangeTo: 10000, dianTechnicalKey: '' };
  niche = 'MINIMERCADO';

  verificationMsg = signal('');
  verificationMode = signal('');
  verificationOk = signal(false);

  async ngOnInit(): Promise<void> {
    await this.refresh();
  }

  async refresh(): Promise<void> {
    this.loading.set(true); this.error.set('');
    try {
      const s = await this.api.status();
      this.applyStatus(s);
    } catch (e: any) {
      this.error.set(e?.error?.message || 'No se pudo cargar el estado de onboarding.');
    } finally {
      this.loading.set(false);
    }
  }

  private applyStatus(s: OnboardingStatusResponse): void {
    this.status.set(s);
    const t = s.tenant;
    this.fiscal.taxRegime = t?.taxRegime || 'NO_RESPONSABLE_IVA';
    this.fiscal.fiscalResponsibilities = t?.fiscalResponsibilities || 'R-99-PN';
    this.fiscal.city = t?.city || '';
    this.fiscal.address = t?.address || '';
    this.fiscal.phone = t?.phone || '';
    this.fiscal.receiptFooterMessage = t?.receiptFooterMessage || 'Gracias por su compra';
    const m = s.mainTerminal;
    if (m) {
      this.dian.dianPrefix = m.dianPrefix || 'POS1';
      this.dian.rangeFrom = m.rangeFrom ?? 1;
      this.dian.rangeTo = m.rangeTo ?? 10000;
      this.dian.dianResolutionNumber = m.dianResolutionNumber || '';
      this.dian.dianResolutionDate = m.dianResolutionDate || '';
      this.dian.dianValidFrom = m.dianValidFrom || '';
      this.dian.dianValidTo = m.dianValidTo || '';
      this.dian.dianTechnicalKey = m.dianTechnicalKey || '';
    }
    // update auth tenant snapshot flags
    const authTenant = this.auth.tenant();
    if (authTenant) {
      this.auth.updateTenant({ ...authTenant, onboardingCompleted: s.onboardingCompleted, onboardingStep: s.onboardingStep });
    }
  }

  async saveFiscal(): Promise<void> {
    this.error.set(''); this.message.set(''); this.saving.set(true);
    try {
      const s = await this.api.saveFiscal({ ...this.fiscal, receiptFooterMessage: this.fiscal.receiptFooterMessage || 'Gracias por su compra' });
      this.applyStatus(s);
      this.message.set('Información fiscal guardada.');
    } catch (e: any) {
      this.error.set(e?.error?.message || 'No se pudo guardar el paso fiscal.');
    } finally { this.saving.set(false); }
  }

  async saveDian(verifyOnly = false): Promise<void> {
    this.error.set(''); this.message.set(''); this.saving.set(true); this.verificationMsg.set('');
    try {
      const res = await this.api.saveDian({
        dianPrefix: this.dian.dianPrefix,
        dianResolutionNumber: this.dian.dianResolutionNumber || null,
        dianResolutionDate: this.dian.dianResolutionDate || null,
        dianValidFrom: this.dian.dianValidFrom || null,
        dianValidTo: this.dian.dianValidTo || null,
        rangeFrom: Number(this.dian.rangeFrom),
        rangeTo: Number(this.dian.rangeTo),
        dianTechnicalKey: this.dian.dianTechnicalKey || null,
        verify: true,
      });
      this.verificationOk.set(res.verified);
      this.verificationMode.set(res.verificationMode);
      this.verificationMsg.set(res.verificationMessage);
      this.applyStatus(res.status);
      if (!verifyOnly) this.message.set('Configuración DIAN guardada.');
    } catch (e: any) {
      this.error.set(e?.error?.message || 'No se pudo guardar/verificar la configuración DIAN.');
    } finally { this.saving.set(false); }
  }

  async seed(action: 'SEED' | 'SKIP'): Promise<void> {
    this.error.set(''); this.message.set(''); this.saving.set(true);
    try {
      const res = await this.api.seedCatalog({ action, niche: this.niche });
      this.applyStatus(res.status);
      this.message.set(res.message);
    } catch (e: any) {
      this.error.set(e?.error?.message || 'No se pudo completar el paso de catálogo.');
    } finally { this.saving.set(false); }
  }

  async goToImport(): Promise<void> {
    // mark catalog step as skipped/completed then navigate
    this.error.set(''); this.saving.set(true);
    try {
      await this.api.seedCatalog({ action: 'SKIP' });
    } catch { /* ignore, still navigate */ }
    finally { this.saving.set(false); }
    this.auth.deferOnboarding();
    await this.router.navigateByUrl('/cargue-masivo');
  }

  async completeLater(): Promise<void> {
    this.auth.deferOnboarding();
    await this.router.navigateByUrl('/pos');
  }

  logout(): void { this.auth.logout(); }
}
