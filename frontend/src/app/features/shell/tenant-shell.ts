import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SyncService } from '../../core/services/sync.service';
import { BranchContextService, BranchService } from '../../core/services/branch.service';
import { AuthService } from '../../core/services/auth.service';
import type { SubscriptionSnapshot } from '../../core/models/pos.models';

@Component({
  selector: 'app-tenant-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
<div class="min-h-screen bg-slate-100">
  <header class="sticky top-0 z-10 border-b border-slate-200 bg-white/95 shadow-sm backdrop-blur">
    <div class="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-3">
      <a routerLink="/pos" class="flex items-center gap-2 font-bold text-slate-800">
        <span class="rounded-lg bg-emerald-600 px-2 py-1 text-white">POS</span>
        <span class="hidden sm:inline">{{ tenantName() }}</span>
      </a>

      <nav class="flex flex-wrap items-center gap-1 text-sm">
        <a routerLink="/pos" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Punto de venta</a>
        <a routerLink="/caja" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Caja</a>
        <a routerLink="/historial" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Historial</a>
        <a routerLink="/inventario" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Inventario</a>
        @if (isAdmin()) {
          <a routerLink="/traslados" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Traslados</a>
          <a routerLink="/sedes" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Sedes y Cajas</a>
          <a routerLink="/cargue-masivo" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Cargue Masivo</a>
          <a routerLink="/reportes" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Reportes</a>
          <a routerLink="/admin/accounting" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Contabilidad</a>
          <a routerLink="/admin/billing" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Facturación</a>
          <a routerLink="/seguridad" routerLinkActive="bg-slate-900 text-white" class="rounded-md px-3 py-1.5 text-slate-600 hover:bg-slate-200">Seguridad</a>
        }
      </nav>

      <div class="ml-auto flex flex-wrap items-center gap-2">
        @if (isAdmin()) {
          <label class="flex items-center gap-2 text-sm text-slate-600" title="Filtro global para módulos administrativos">
            <span class="hidden lg:inline font-medium">Sede:</span>
            <select class="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm"
                    [value]="branchCtx.selectedBranchId() ?? ''" (change)="onBranchChange($event)">
              <option value="">Todas las sedes</option>
              @for (b of branchCtx.branches(); track b.id) {
                <option [value]="b.id">{{ b.name }}{{ b.main ? ' (Principal)' : '' }}</option>
              }
            </select>
          </label>
        }

        <span class="h-2.5 w-2.5 rounded-full" [class.bg-emerald-500]="sync.online()" [class.bg-rose-500]="!sync.online()"></span>
        <span class="text-sm font-medium" [class.text-emerald-700]="sync.online()" [class.text-rose-700]="!sync.online()">
          {{ sync.online() ? 'Online' : 'Offline' }}
        </span>
        <span class="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800">{{ sync.pendingCount() }} pendiente(s)</span>
        <button type="button" (click)="sync.synchronize()" [disabled]="sync.syncing()"
                class="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50">
          {{ sync.syncing() ? 'Sincronizando...' : 'Sincronizar' }}
        </button>
        <div class="flex items-center gap-2 border-l border-slate-200 pl-2 ml-1">
          <span class="text-xs text-slate-600 hidden md:inline">{{ userName() }}</span>
          <button type="button" (click)="logout()" class="rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100">Salir</button>
        </div>
      </div>
    </div>
    <div class="border-t border-slate-100 bg-slate-50 px-4 py-1.5 text-center text-xs text-slate-500">
      {{ sync.message() }} @if (sync.persistent()) { <span>· Almacenamiento persistente activo</span> }
    </div>

    @if (subscription(); as sub) {
      @if (sub.subscriptionStatus === 'PAST_DUE') {
        <div class="border-b border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-900">
          <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2">
            <span><strong>Suscripción en periodo de gracia.</strong> Puedes seguir facturando por {{ sub.graceDaysRemaining }} día(s). Renueva para evitar la suspensión.</span>
            @if (isAdmin()) { <a routerLink="/admin/billing" class="font-semibold underline">Renovar suscripción</a> }
          </div>
        </div>
      }
      @if (sub.subscriptionStatus === 'SUSPENDED') {
        <div class="border-b border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-900">
          <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2">
            <span><strong>Suscripción suspendida.</strong> No puedes crear nuevas ventas. Reportes, historial y exportación contable siguen disponibles.</span>
            @if (isAdmin()) { <a routerLink="/admin/billing" class="font-semibold underline">Renovar suscripción</a> }
          </div>
        </div>
      }
    }
  </header>
  <main class="mx-auto max-w-7xl px-4 py-6">
    <router-outlet />
  </main>
</div>
  `
})
export class TenantShell implements OnInit {
  readonly sync = inject(SyncService);
  readonly branchCtx = inject(BranchContextService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly subscription = signal<SubscriptionSnapshot | undefined>(undefined);
  readonly tenantName = computed(() => this.auth.tenant()?.tradeName || this.auth.tenant()?.businessName || 'POS');
  readonly userName = computed(() => this.auth.user()?.fullName || '');
  readonly isAdmin = computed(() => this.auth.user()?.role === 'ADMIN');

  constructor() {
    effect(() => {
      const at = this.sync.lastSyncAt();
      if (at) {
        void this.loadBranches();
        void this.loadSubscription();
      }
    });
  }

  ngOnInit(): void {
    if (this.auth.isAuthenticated() && !this.auth.isSuperAdmin()) {
      void this.sync.init();
      void this.loadBranches();
      void this.loadSubscription();
    }
  }

  async loadBranches(): Promise<void> {
    if (!this.sync.online()) return;
    try {
      const branches = await this.branchService.listBranches();
      this.branchCtx.setBranches(branches);
    } catch { /* ignore */ }
  }

  async loadSubscription(): Promise<void> {
    try {
      this.subscription.set(await this.sync.subscriptionSnapshot());
    } catch { /* ignore */ }
  }

  onBranchChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.branchCtx.setSelectedBranchId(value || null);
  }

  logout(): void { this.auth.logout(); }
}
