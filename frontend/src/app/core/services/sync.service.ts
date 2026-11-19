import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, interval } from 'rxjs';
import { db, readConfig, requestPersistentStorage, sequenceKey } from '../db/pos-offline.database';
import { environment } from '../../../environments/environment';
import {
  type OutboxEvent,
  type PullResponse,
  type PushResponse,
  type SyncEventResult,
  type TerminalConfig,
} from '../models/pos.models';

@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly online = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  readonly pendingCount = signal(0);
  readonly syncing = signal(false);
  readonly lastSyncAt = signal<string | null>(null);
  readonly persistent = signal(false);
  readonly message = signal('Preparando almacenamiento local…');

  private started = false;
  private running = false;

  constructor() {
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);
    this.destroyRef.onDestroy(() => {
      window.removeEventListener('online', this.onOnline);
      window.removeEventListener('offline', this.onOffline);
    });
  }

  async start(): Promise<void> {
    if (this.started) {
      return;
    }
    this.started = true;
    this.persistent.set(await requestPersistentStorage());
    await this.refreshPendingCount();

    interval(environment.syncIntervalMs)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.synchronize());

    if (navigator.onLine) {
      await this.pull();
    } else {
      this.message.set('Sin conexión: las ventas se guardan localmente');
    }
  }

  terminal(): Promise<TerminalConfig | undefined> {
    return readConfig<TerminalConfig>('terminal');
  }

  async pull(): Promise<void> {
    if (!navigator.onLine) {
      this.online.set(false);
      return;
    }

    try {
      const cursor = (await readConfig<string>('sync.cursor')) ?? new Date(0).toISOString();
      const response = await firstValueFrom(
        this.http.get<PullResponse>(`${environment.apiBaseUrl}/sync/pull`, {
          params: { since: cursor },
        }),
      );

      await db.transaction('rw', db.config, db.products, db.customers, async () => {
        for (const product of response.products) {
          await db.products.put(product);
        }
        for (const customer of response.customers) {
          await db.customers.put(customer);
        }

        await db.config.put({
          key: 'terminal',
          value: response.terminal,
          updatedAt: response.serverTime,
        });
        await db.config.put({
          key: 'sync.cursor',
          value: response.serverTime,
          updatedAt: response.serverTime,
        });

        const key = sequenceKey(response.terminal.dianPrefix);
        const local = await db.config.get(key);
        const localSequence = typeof local?.value === 'number' ? local.value : 0;
        if (response.terminal.lastSyncedSequence > localSequence) {
          await db.config.put({
            key,
            value: response.terminal.lastSyncedSequence,
            updatedAt: response.serverTime,
          });
        }
      });

      this.lastSyncAt.set(response.serverTime);
      this.online.set(true);
      this.message.set('Catálogo actualizado');
    } catch (error) {
      this.online.set(false);
      this.message.set(`No se pudo descargar el catálogo: ${this.errorMessage(error)}`);
    }
  }

  async synchronize(): Promise<void> {
    if (this.running) {
      return;
    }
    this.running = true;
    try {
      await this.refreshPendingCount();
      if (!navigator.onLine) {
        this.online.set(false);
        this.message.set('Sin conexión: las ventas se guardan localmente');
        return;
      }
      await this.pushOutbox();
      await this.flushPendingInventoryMovements();
      await this.pull();
      await this.refreshPendingCount();
    } finally {
      this.running = false;
    }
  }

  async flushPendingInventoryMovements(): Promise<void> {
    if (!navigator.onLine) {
      return;
    }
    const pending = await db.inventoryMovements
      .filter((m) => m.pendingSync === true)
      .sortBy('createdAt');

    for (const movement of pending) {
      try {
        await firstValueFrom(
          this.http.post(`${environment.apiBaseUrl}/inventory/movements`, {
            productId: movement.productId,
            movementType: movement.movementType,
            quantity: movement.quantity,
            notes: movement.notes ?? 'Movimiento local pendiente',
            createdBy: movement.createdBy ?? null,
          }),
        );
        await db.inventoryMovements.put({ ...movement, pendingSync: false });
      } catch (error) {
        this.online.set(false);
        this.message.set(`Movimiento pendiente no enviado: ${this.errorMessage(error)}`);
        break;
      }
    }
  }

  private async pushOutbox(): Promise<void> {
    if (this.syncing()) {
      return;
    }

    const events = await db.outbox.orderBy('createdAt').limit(50).toArray();
    if (events.length === 0) {
      return;
    }

    this.syncing.set(true);
    this.message.set(`Enviando ${events.length} venta(s) pendiente(s)…`);

    try {
      const response = await firstValueFrom(
        this.http.post<PushResponse>(`${environment.apiBaseUrl}/sync/push`, {
          events: events.map((event) => ({
            idempotencyKey: event.idempotencyKey,
            entityType: event.entityType,
            payload: event.payload,
          })),
        }),
      );

      const results = new Map<string, SyncEventResult>(
        response.results.map((result) => [result.idempotencyKey, result]),
      );

      await db.transaction('rw', db.outbox, db.salesHistory, db.config, async () => {
        let maxSequence = 0;
        let prefix = '';

        for (const event of events) {
          const result = results.get(event.idempotencyKey);
          if (!result) {
            continue;
          }

          prefix = event.payload.dianPrefix;
          if (result.status === 'PROCESSED' || result.status === 'DUPLICATE_IGNORED') {
            maxSequence = Math.max(maxSequence, event.payload.sequenceNumber);
            await db.outbox.delete(event.idempotencyKey);
            const history = await db.salesHistory.get(event.payload.id);
            if (history) {
              await db.salesHistory.put({
                ...history,
                status: 'SYNCED',
                syncedAt: response.processedAt,
              });
            }
          } else {
            await db.outbox.delete(event.idempotencyKey);
            const history = await db.salesHistory.get(event.payload.id);
            if (history) {
              await db.salesHistory.put({
                ...history,
                status: 'REJECTED_CONFLICT',
                lastError: result.message,
              });
            }
          }
        }

        if (prefix && maxSequence > 0) {
          const key = sequenceKey(prefix);
          const stored = await db.config.get(key);
          const current = typeof stored?.value === 'number' ? stored.value : 0;
          if (maxSequence > current) {
            await db.config.put({ key, value: maxSequence, updatedAt: response.processedAt });
          }
        }
      });

      this.message.set('Sincronización completada');
    } catch (error) {
      const detail = this.errorMessage(error);
      for (const event of events) {
        await db.outbox.update(event.idempotencyKey, {
          attempts: event.attempts + 1,
          lastError: detail,
        });
      }
      this.online.set(false);
      this.message.set(`Reintentaremos en 15 s: ${detail}`);
    } finally {
      this.syncing.set(false);
      await this.refreshPendingCount();
    }
  }

  private async refreshPendingCount(): Promise<void> {
    this.pendingCount.set(await db.outbox.count());
  }

  private errorMessage(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    return String(error);
  }

  private readonly onOnline = (): void => {
    this.online.set(true);
    void this.synchronize();
  };

  private readonly onOffline = (): void => {
    this.online.set(false);
    this.message.set('Sin conexión: las ventas se guardan localmente');
  };
}
