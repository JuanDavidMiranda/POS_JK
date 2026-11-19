import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { SecurityService, type UserSummary } from '../../core/services/security.service';
import { SyncService } from '../../core/services/sync.service';
import type { SecurityPolicy } from '../../core/models/pos.models';

@Component({
  selector: 'app-security',
  templateUrl: './security.html',
  styleUrl: './security.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Security implements OnInit {
  private readonly securityService = inject(SecurityService);
  readonly sync = inject(SyncService);

  readonly policy = signal<SecurityPolicy>({
    requirePinForDiscounts: true,
    maxCashierDiscountPercent: 0,
    requirePinForVoidItem: true,
    blindCashClose: true,
  });
  readonly users = signal<UserSummary[]>([]);
  readonly selectedUserId = signal('');
  readonly pin = signal('');
  readonly feedback = signal('');
  readonly error = signal('');
  readonly busy = signal(false);

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  async load(): Promise<void> {
    this.error.set('');
    try {
      const [policy, users] = await Promise.all([
        this.securityService.getPolicies(),
        this.securityService.listUsers(),
      ]);
      this.policy.set(policy);
      this.users.set(users);
      const admin = users.find((u) => u.role === 'ADMIN') ?? users[0];
      if (admin) this.selectedUserId.set(admin.id);
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible cargar la configuración de seguridad');
    }
  }

  updateField<K extends keyof SecurityPolicy>(key: K, value: SecurityPolicy[K]): void {
    this.policy.update((p) => ({ ...p, [key]: value }));
  }

  async savePolicy(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.feedback.set('');
    try {
      const saved = await this.securityService.updatePolicies(this.policy());
      this.policy.set(saved);
      this.feedback.set('Políticas guardadas. Sincroniza el POS para aplicarlas offline.');
      await this.sync.pull().catch(() => {});
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible guardar las políticas');
    } finally {
      this.busy.set(false);
    }
  }

  async savePin(): Promise<void> {
    const userId = this.selectedUserId();
    const pin = this.pin().trim();
    if (!userId) { this.error.set('Selecciona un usuario'); return; }
    if (!/^\d{4,6}$/.test(pin)) { this.error.set('El PIN debe tener entre 4 y 6 dígitos'); return; }
    this.busy.set(true);
    this.error.set('');
    this.feedback.set('');
    try {
      await this.securityService.setPin(userId, pin);
      this.pin.set('');
      this.feedback.set('PIN actualizado. Sincroniza el POS para habilitar la autorización offline.');
      await this.load();
      await this.sync.pull().catch(() => {});
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible actualizar el PIN');
    } finally {
      this.busy.set(false);
    }
  }

  onUserChange(event: Event): void {
    this.selectedUserId.set((event.target as HTMLSelectElement).value);
  }

  onPinInput(event: Event): void {
    this.pin.set((event.target as HTMLInputElement).value);
  }

  onMaxDiscountInput(event: Event): void {
    const v = Number((event.target as HTMLInputElement).value);
    this.updateField('maxCashierDiscountPercent', Number.isFinite(v) ? v : 0);
  }
}