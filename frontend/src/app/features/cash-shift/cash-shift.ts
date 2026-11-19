import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { SyncService } from '../../core/services/sync.service';
import { CashShiftService, type CashShiftResponse } from '../../core/services/cash-shift.service';
import { PinAuthService } from '../../core/services/pin-auth.service';
import { SupervisorPinModal } from '../../shared/components/supervisor-pin-modal/supervisor-pin-modal';
import { readConfig } from '../../core/db/pos-offline.database';
import { securityPolicyKey } from '../../core/db/pos-offline.database';
import type { SecurityPolicy } from '../../core/models/pos.models';

@Component({
  selector: 'app-cash-shift',
  templateUrl: './cash-shift.html',
  styleUrl: './cash-shift.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, SupervisorPinModal],
})
export class CashShift implements OnInit {
  private readonly cashShiftService = inject(CashShiftService);
  private readonly pinAuth = inject(PinAuthService);
  readonly sync = inject(SyncService);

  readonly current = signal<CashShiftResponse | null>(null);
  readonly history = signal<CashShiftResponse[]>([]);
  readonly openingAmount = signal<number>(0);
  readonly countedAmount = signal<number>(0);
  readonly notes = signal('');
  readonly feedback = signal('');
  readonly error = signal('');
  readonly busy = signal(false);

  readonly blindClose = signal(true);
  readonly countedEntered = signal(false);
  readonly lastClosed = signal<CashShiftResponse | null>(null);

  readonly pinOpen = signal(false);
  readonly pinError = signal('');
  readonly pinBusy = signal(false);
  private pendingClose = false;

  async ngOnInit(): Promise<void> {
    const p = await readConfig<SecurityPolicy>(securityPolicyKey);
    if (p) this.blindClose.set(!!p.blindCashClose);
    await this.reload();
  }

  async reload(): Promise<void> {
    this.error.set('');
    try {
      this.current.set(await this.cashShiftService.current());
      this.history.set(await this.cashShiftService.recent());
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible cargar los turnos (requiere conexión al servidor).');
    }
  }

  async openShift(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.feedback.set('');
    try {
      const terminal = await this.sync.terminalConfig();
      const shift = await this.cashShiftService.open({
        terminalId: terminal?.id ?? null,
        openingAmount: Number(this.openingAmount()) || 0,
      });
      this.current.set(shift);
      this.feedback.set('Turno abierto correctamente.');
      await this.reload();
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible abrir el turno');
    } finally {
      this.busy.set(false);
    }
  }

  onCountedInput(event: Event): void {
    this.countedAmount.set(Number((event.target as HTMLInputElement).value) || 0);
  }

  confirmCounted(): void {
    this.countedEntered.set(true);
  }

  requestClose(): void {
    if (!this.countedEntered() && this.blindClose()) {
      this.error.set('Ingresa y confirma el conteo físico antes de ver el esperado.');
      return;
    }
    // cierre auditado: solicitar PIN de supervisor si se dispone (recomendado) / registrar autorización
    this.pendingClose = true;
    this.pinOpen.set(true);
    this.pinError.set('');
  }

  async onPinSubmit(pin: string): Promise<void> {
    this.pinBusy.set(true);
    this.pinError.set('');
    try {
      const auth = await this.pinAuth.authorize(pin);
      if (!auth) {
        this.pinError.set('PIN inválido.');
        return;
      }
      this.pinOpen.set(false);
      await this.doClose(auth.authorizedBy);
    } finally {
      this.pinBusy.set(false);
    }
  }

  onPinCancel(): void {
    // permitir cierre sin PIN solo si no se pudo validar pero el usuario decide? mejor cerrar flujo; para auditoría se requiere PIN si hay supervisor.
    this.pinOpen.set(false);
    this.pendingClose = false;
  }

  async closeWithoutPin(): Promise<void> {
    this.pinOpen.set(false);
    await this.doClose(null);
  }

  private async doClose(authorizedBy: string | null): Promise<void> {
    const shift = this.current();
    if (!shift) return;
    if (!this.countedEntered() && this.blindClose()) {
      this.error.set('Debes confirmar el conteo físico antes de cerrar.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.feedback.set('');
    try {
      const closed = await this.cashShiftService.close(shift.id, {
        authorizedByUserId: authorizedBy,
        countedCashAmount: Number(this.countedAmount()) || 0,
        notes: this.notes().trim() || undefined,
      });
      this.lastClosed.set(closed);
      this.current.set(null);
      this.countedEntered.set(false);
      this.countedAmount.set(0);
      this.notes.set('');
      this.feedback.set('Turno cerrado. Se registró la auditoría del cierre.');
      await this.reload();
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible cerrar el turno');
    } finally {
      this.busy.set(false);
      this.pendingClose = false;
    }
  }
}