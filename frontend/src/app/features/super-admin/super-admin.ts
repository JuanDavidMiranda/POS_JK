import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { SuperAdminApiService } from '../../core/services/super-admin-api.service';
import type { SuperAdminDashboard, SuperAdminSubscriptionUpdate, SuperAdminTenantHealth, SuperAdminTenantItem } from '../../core/models/auth.models';

@Component({
  selector: 'app-super-admin',
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
<div class="min-h-screen bg-slate-100">
  <header class="sticky top-0 z-20 border-b border-slate-200 bg-slate-900 text-white">
    <div class="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
      <div class="flex items-center gap-3">
        <span class="rounded-lg bg-emerald-500 px-2 py-1 font-bold text-white">POS</span>
        <span class="font-semibold">Panel Super-Admin</span>
        <span class="rounded bg-slate-700 px-2 py-0.5 text-xs text-slate-200">Plataforma</span>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span class="text-slate-300">{{ auth.user()?.fullName }}</span>
        <button type="button" (click)="logout()" class="rounded-md border border-slate-600 px-3 py-1.5 hover:bg-slate-800">Salir</button>
      </div>
    </div>
  </header>

  <main class="mx-auto max-w-7xl px-4 py-6 space-y-6">
    @if (error()) { <div class="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error() }}</div> }
    @if (toast()) { <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{{ toast() }}</div> }

    @if (dashboard()) {
      <section class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <div class="rounded-xl bg-white p-4 shadow-sm border border-slate-200">
          <div class="text-xs text-slate-500">Empresas totales</div>
          <div class="text-2xl font-bold text-slate-800">{{ dashboard()!.totalCompanies }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 shadow-sm border border-slate-200">
          <div class="text-xs text-slate-500">Activas</div>
          <div class="text-2xl font-bold text-emerald-700">{{ dashboard()!.activeCompanies }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 shadow-sm border border-slate-200">
          <div class="text-xs text-slate-500">En prueba (TRIAL)</div>
          <div class="text-2xl font-bold text-amber-700">{{ dashboard()!.trialCompanies }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 shadow-sm border border-slate-200">
          <div class="text-xs text-slate-500">Vencidas/Suspendidas</div>
          <div class="text-2xl font-bold text-rose-700">{{ dashboard()!.expiredOrSuspendedCompanies }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 shadow-sm border border-slate-200">
          <div class="text-xs text-slate-500">Ventas hoy (global)</div>
          <div class="text-lg font-bold text-slate-800">{{ dashboard()!.totalSalesTodayCount }}</div>
          <div class="text-xs text-slate-500">{{ money(dashboard()!.totalSalesTodayAmount) }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 shadow-sm border border-slate-200">
          <div class="text-xs text-slate-500">Folios DIAN usados</div>
          <div class="text-2xl font-bold text-slate-800">{{ dashboard()!.totalDianFoliosUsedThisPeriod }}</div>
        </div>
      </section>
    }

    <section class="rounded-xl bg-white shadow-sm border border-slate-200">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
        <h2 class="font-semibold text-slate-800">Empresas registradas</h2>
        <div class="flex flex-wrap items-center gap-2">
          <input [(ngModel)]="q" (keyup.enter)="loadTenants()" placeholder="Buscar por NIT o nombre…" class="rounded-md border border-slate-300 px-3 py-1.5 text-sm w-64" />
          <select [(ngModel)]="statusFilter" (change)="loadTenants()" class="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
            <option value="ALL">Todos</option>
            <option value="TRIAL">Trial</option>
            <option value="ACTIVE">Activos</option>
            <option value="PAST_DUE">Mora (PAST_DUE)</option>
            <option value="SUSPENDED">Suspendidos</option>
          </select>
          <button type="button" (click)="loadTenants()" class="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700">Buscar</button>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-left text-slate-600">
            <tr>
              <th class="px-4 py-2 font-medium">Empresa</th>
              <th class="px-4 py-2 font-medium">Plan / Estado</th>
              <th class="px-4 py-2 font-medium">Vencimiento</th>
              <th class="px-4 py-2 font-medium">Folios DIAN</th>
              <th class="px-4 py-2 font-medium">Sedes / Cajas</th>
              <th class="px-4 py-2 font-medium">Última sync</th>
              <th class="px-4 py-2 font-medium">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @if (loading()) {
              <tr><td colspan="7" class="px-4 py-6 text-center text-slate-500">Cargando…</td></tr>
            } @else if (tenants().length === 0) {
              <tr><td colspan="7" class="px-4 py-6 text-center text-slate-500">No hay empresas para mostrar.</td></tr>
            } @else {
              @for (t of tenants(); track t.id) {
                <tr class="border-t border-slate-100 hover:bg-slate-50">
                  <td class="px-4 py-3">
                    <div class="font-medium text-slate-800">{{ t.tradeName || t.businessName }}</div>
                    <div class="text-xs text-slate-500">{{ t.nit }} · {{ t.businessName }}</div>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex flex-wrap gap-1">
                      <span class="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">{{ t.planCode }}</span>
                      <span class="rounded px-2 py-0.5 text-xs font-semibold"
                            [class.bg-emerald-100]="t.subscriptionStatus==='ACTIVE'" [class.text-emerald-800]="t.subscriptionStatus==='ACTIVE'"
                            [class.bg-amber-100]="t.subscriptionStatus==='PAST_DUE' || t.planCode==='TRIAL'" [class.text-amber-800]="t.subscriptionStatus==='PAST_DUE' || t.planCode==='TRIAL'"
                            [class.bg-rose-100]="t.subscriptionStatus==='SUSPENDED'" [class.text-rose-800]="t.subscriptionStatus==='SUSPENDED'">
                        {{ t.subscriptionStatus }}
                      </span>
                    </div>
                  </td>
                  <td class="px-4 py-3 text-slate-700">
                    {{ t.subscriptionEndsAt ? (t.subscriptionEndsAt | date:'yyyy-MM-dd') : (t.trialEndsAt ? ('Trial: ' + (t.trialEndsAt | date:'yyyy-MM-dd')) : '—') }}
                  </td>
                  <td class="px-4 py-3 min-w-[160px]">
                    <div class="text-xs text-slate-600 mb-1">{{ t.electronicFoliosUsed }} / {{ t.electronicFoliosLimit }}</div>
                    <div class="h-2 w-full rounded-full bg-slate-200 overflow-hidden">
                      <div class="h-2 rounded-full" [style.width.%]="folioPercent(t)"
                           [class.bg-emerald-500]="folioPercent(t) < 70" [class.bg-amber-500]="folioPercent(t) >= 70 && folioPercent(t) < 90" [class.bg-rose-500]="folioPercent(t) >= 90"></div>
                    </div>
                  </td>
                  <td class="px-4 py-3 text-slate-700">{{ t.branchCount }} / {{ t.terminalCount }}</td>
                  <td class="px-4 py-3 text-slate-600 text-xs">{{ t.lastSyncAt ? (t.lastSyncAt | date:'yyyy-MM-dd HH:mm') : 'Sin sincronización' }}</td>
                  <td class="px-4 py-3">
                    <div class="flex flex-wrap gap-2">
                      <button type="button" (click)="openSubscription(t)" class="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium hover:bg-slate-100">Gestionar</button>
                      <button type="button" (click)="openHealth(t)" class="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium hover:bg-slate-100">Diagnóstico</button>
                    </div>
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>
      <div class="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm text-slate-600">
        <span>Total: {{ totalElements() }}</span>
        <div class="flex items-center gap-2">
          <button type="button" (click)="prevPage()" [disabled]="page()===0" class="rounded border border-slate-300 px-2 py-1 disabled:opacity-40">Anterior</button>
          <span>Página {{ page()+1 }} / {{ maxPage() }}</span>
          <button type="button" (click)="nextPage()" [disabled]="page()+1 >= totalPages()" class="rounded border border-slate-300 px-2 py-1 disabled:opacity-40">Siguiente</button>
        </div>
      </div>
    </section>
  </main>

  @if (subModalOpen()) {
    <div class="fixed inset-0 z-30 flex items-center justify-center bg-black/40 p-4">
      <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-1">Gestionar suscripción y folios</h3>
        <p class="text-sm text-slate-500 mb-4">{{ selectedTenant()?.businessName }} ({{ selectedTenant()?.nit }})</p>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <label class="block">
            <span class="text-sm font-medium text-slate-700">Plan</span>
            <select [(ngModel)]="subForm.planCode" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2">
              <option value="TRIAL">TRIAL</option>
              <option value="BASIC">BASIC</option>
              <option value="PRO">PRO</option>
              <option value="MULTI_BRANCH">MULTI_BRANCH</option>
            </select>
          </label>
          <label class="block">
            <span class="text-sm font-medium text-slate-700">Estado</span>
            <select [(ngModel)]="subForm.subscriptionStatus" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2">
              <option value="ACTIVE">ACTIVE</option>
              <option value="PAST_DUE">PAST_DUE</option>
              <option value="SUSPENDED">SUSPENDED</option>
            </select>
          </label>
          <label class="block">
            <span class="text-sm font-medium text-slate-700">Vencimiento suscripción</span>
            <input [(ngModel)]="subForm.subscriptionEndsAtDate" type="date" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" />
          </label>
          <label class="block">
            <span class="text-sm font-medium text-slate-700">Vencimiento trial</span>
            <input [(ngModel)]="subForm.trialEndsAtDate" type="date" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" />
          </label>
          <label class="block">
            <span class="text-sm font-medium text-slate-700">Nuevo límite de folios</span>
            <input [(ngModel)]="subForm.electronicFoliosLimit" type="number" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="(opcional)" />
          </label>
          <label class="block">
            <span class="text-sm font-medium text-slate-700">Recargar folios (+N)</span>
            <input [(ngModel)]="subForm.addElectronicFolios" type="number" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="(opcional)" />
          </label>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <button type="button" (click)="subModalOpen.set(false)" class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-50">Cancelar</button>
          <button type="button" (click)="saveSubscription()" [disabled]="savingSub()" class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">{{ savingSub() ? 'Guardando…' : 'Guardar cambios' }}</button>
        </div>
      </div>
    </div>
  }

  @if (healthOpen()) {
    <div class="fixed inset-0 z-30 flex justify-end bg-black/30">
      <div class="h-full w-full max-w-xl bg-white shadow-2xl flex flex-col">
        <div class="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div>
            <h3 class="text-lg font-semibold text-slate-800">Diagnóstico técnico</h3>
            <p class="text-sm text-slate-500">{{ health()?.businessName }} ({{ health()?.nit }})</p>
          </div>
          <button type="button" (click)="healthOpen.set(false)" class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">Cerrar</button>
        </div>
        <div class="flex-1 overflow-y-auto p-5 space-y-5">
          @if (healthLoading()) {
            <div class="text-slate-500">Cargando diagnóstico…</div>
          } @else if (health()) {
            <section>
              <h4 class="font-semibold text-slate-800 mb-2">Cajas / Terminales ({{ health()!.terminals.length }})</h4>
              <div class="space-y-2">
                @for (t of health()!.terminals; track t.id) {
                  <div class="rounded-lg border border-slate-200 p-3 text-sm">
                    <div class="flex items-center justify-between">
                      <span class="font-medium">{{ t.name }} <span class="text-slate-500">({{ t.dianPrefix }})</span></span>
                      <span class="rounded px-2 py-0.5 text-xs font-semibold" [class.bg-emerald-100]="t.active" [class.text-emerald-800]="t.active" [class.bg-slate-200]="!t.active" [class.text-slate-700]="!t.active">{{ t.active ? 'Activa' : 'Inactiva' }}</span>
                    </div>
                    <div class="text-xs text-slate-500 mt-1">Sede: {{ t.branchName || '—' }} · Actualizada: {{ t.updatedAt ? (t.updatedAt | date:'yyyy-MM-dd HH:mm') : '—' }}</div>
                  </div>
                }
                @if (health()!.terminals.length === 0) { <div class="text-sm text-slate-500">Sin cajas registradas.</div> }
              </div>
            </section>

            <section>
              <h4 class="font-semibold text-slate-800 mb-2">Últimas sincronizaciones</h4>
              @for (s of health()!.recentSyncs; track $index) {
                <div class="rounded-lg border border-slate-200 p-3 text-sm">
                  <div><strong>Entidad:</strong> {{ s.lastEntityType || '—' }}</div>
                  <div class="text-slate-600"><strong>Fecha:</strong> {{ s.lastSyncAt ? (s.lastSyncAt | date:'yyyy-MM-dd HH:mm:ss') : '—' }}</div>
                </div>
              }
              @if (health()!.recentSyncs.length === 0) { <div class="text-sm text-slate-500">Sin registros de sincronización.</div> }
            </section>

            <section>
              <h4 class="font-semibold text-slate-800 mb-2">Ventas / NC con error o pendientes DIAN</h4>
              <div class="space-y-2">
                @for (i of health()!.dianPendingOrErrors; track i.id) {
                  <div class="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
                    <div class="flex items-center justify-between">
                      <span class="font-medium">{{ i.type }} {{ i.documentNumber || '' }}</span>
                      <span class="rounded bg-amber-200 px-2 py-0.5 text-xs font-semibold text-amber-900">{{ i.dianStatus }}</span>
                    </div>
                    <div class="text-xs text-slate-700 mt-1">{{ i.detail }}</div>
                    <div class="text-xs text-slate-500">{{ i.createdAt ? (i.createdAt | date:'yyyy-MM-dd HH:mm') : '' }}</div>
                  </div>
                }
                @if (health()!.dianPendingOrErrors.length === 0) { <div class="text-sm text-emerald-700">Sin documentos DIAN pendientes o con error.</div> }
              </div>
            </section>
          }
        </div>
      </div>
    </div>
  }
</div>
  `
})
export class SuperAdminComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(SuperAdminApiService);

  readonly dashboard = signal<SuperAdminDashboard | null>(null);
  readonly tenants = signal<SuperAdminTenantItem[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly toast = signal('');
  q = '';
  statusFilter = 'ALL';
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly maxPage = computed(() => Math.max(this.totalPages(), 1));

  readonly subModalOpen = signal(false);
  readonly selectedTenant = signal<SuperAdminTenantItem | null>(null);
  readonly savingSub = signal(false);
  subForm: any = { planCode: 'TRIAL', subscriptionStatus: 'ACTIVE', subscriptionEndsAtDate: '', trialEndsAtDate: '', electronicFoliosLimit: null, addElectronicFolios: null };

  readonly healthOpen = signal(false);
  readonly healthLoading = signal(false);
  readonly health = signal<SuperAdminTenantHealth | null>(null);

  async ngOnInit(): Promise<void> {
    await Promise.all([this.loadDashboard(), this.loadTenants()]);
  }

  async loadDashboard(): Promise<void> {
    try { this.dashboard.set(await this.api.dashboard()); } catch (e: any) { this.error.set(e?.error?.message || 'No se pudo cargar el dashboard.'); }
  }

  async loadTenants(): Promise<void> {
    this.loading.set(true); this.error.set('');
    try {
      const res = await this.api.tenants(this.q.trim(), this.statusFilter, this.page(), 20);
      this.tenants.set(res.content);
      this.totalPages.set(res.totalPages);
      this.totalElements.set(res.totalElements);
    } catch (e: any) {
      this.error.set(e?.error?.message || 'No se pudo cargar la lista de empresas.');
    } finally { this.loading.set(false); }
  }

  prevPage(): void { if (this.page() > 0) { this.page.set(this.page() - 1); void this.loadTenants(); } }
  nextPage(): void { if (this.page() + 1 < this.totalPages()) { this.page.set(this.page() + 1); void this.loadTenants(); } }

  folioPercent(t: SuperAdminTenantItem): number {
    if (!t.electronicFoliosLimit) return 0;
    return Math.min(100, Math.round((t.electronicFoliosUsed / t.electronicFoliosLimit) * 100));
  }

  money(v: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v ?? 0);
  }

  openSubscription(t: SuperAdminTenantItem): void {
    this.selectedTenant.set(t);
    this.subForm = {
      planCode: t.planCode,
      subscriptionStatus: t.subscriptionStatus,
      subscriptionEndsAtDate: t.subscriptionEndsAt ? t.subscriptionEndsAt.substring(0, 10) : '',
      trialEndsAtDate: t.trialEndsAt ? t.trialEndsAt.substring(0, 10) : '',
      electronicFoliosLimit: null,
      addElectronicFolios: null,
    };
    this.subModalOpen.set(true);
  }

  async saveSubscription(): Promise<void> {
    const t = this.selectedTenant(); if (!t) return;
    this.savingSub.set(true); this.error.set(''); this.toast.set('');
    try {
      const req: SuperAdminSubscriptionUpdate = {
        planCode: this.subForm.planCode,
        subscriptionStatus: this.subForm.subscriptionStatus,
      };
      if (this.subForm.subscriptionEndsAtDate) req.subscriptionEndsAt = this.subForm.subscriptionEndsAtDate + 'T23:59:59Z';
      if (this.subForm.trialEndsAtDate) req.trialEndsAtDate = this.subForm.trialEndsAtDate;
      if (this.subForm.electronicFoliosLimit != null && this.subForm.electronicFoliosLimit !== '') req.electronicFoliosLimit = Number(this.subForm.electronicFoliosLimit);
      if (this.subForm.addElectronicFolios != null && this.subForm.addElectronicFolios !== '') req.addElectronicFolios = Number(this.subForm.addElectronicFolios);
      await this.api.updateSubscription(t.id, req);
      this.subModalOpen.set(false);
      this.toast.set('Suscripción actualizada correctamente.');
      await Promise.all([this.loadDashboard(), this.loadTenants()]);
      setTimeout(() => this.toast.set(''), 4000);
    } catch (e: any) {
      this.error.set(e?.error?.message || 'No se pudo actualizar la suscripción.');
    } finally { this.savingSub.set(false); }
  }

  async openHealth(t: SuperAdminTenantItem): Promise<void> {
    this.healthOpen.set(true); this.healthLoading.set(true); this.health.set(null);
    try { this.health.set(await this.api.health(t.id)); }
    catch (e: any) { this.error.set(e?.error?.message || 'No se pudo cargar el diagnóstico.'); this.healthOpen.set(false); }
    finally { this.healthLoading.set(false); }
  }

  logout(): void { this.auth.logout(); }
}
