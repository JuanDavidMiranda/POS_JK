import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, interval } from 'rxjs';
import { db, lastPullKey, readConfig, requestPersistentStorage, securityPolicyKey, sequenceKey, supervisorPinsKey, terminalConfigKey } from '../db/pos-offline.database';
import { environment } from '../../../environments/environment';
import {
  type OutboxEvent,
  type PullResponse,
  type PushResponse,
  type SecurityPolicy,
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

  async init(): Promise<void> {
    if (this.started) {
      return;
    }
    this.started = true;
    this.persistent.set(await requestPersistentStorage());
    await this.refreshPendingCount();
    this.message.set(this.persistent() ? 'IndexedDB listo (almacenamiento persistente)' : 'IndexedDB listo');

    interval(15000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.online() && !this.running) {
          void this.syncNow();
        }
      });

    if (this.online()) {
      void this.syncNow();
    }
  }

  async pull(): Promise<void> {
    const terminal = await this.terminalConfig();
    const lastPull = await readConfig<string>(lastPullKey);
    const params: Record<string, string> = {};
    if (terminal?.id) {
      params['terminalId'] = terminal.id;
    } else {
      params['terminalId'] = '33333333-3333-3333-3333-333333333333';
    }
    if (lastPull) {
      params['since'] = lastPull;
    }
    const response = await firstValueFrom(
      this.http.get<PullResponse>(`${environment.apiBaseUrl}/api/v1/sync/pull`, { params }),
    );
    await this.applyPull(response);
    this.lastSyncAt.set(response.serverTime);
  }

  async push(): Promise<void> {
    const events = await db.outbox.orderBy('createdAt').toArray();
    if (events.length === 0) {
      await this.refreshPendingCount();
      return;
    }

    const response = await firstValueFrom(
      this.http.post<PushResponse>(`${environment.apiBaseUrl}/api/v1/sync/push`, { events }),
    );

    for (const result of response.results) {
      await this.applyResult(result);
    }
    await this.refreshPendingCount();
    this.lastSyncAt.set(response.processedAt);
  }

  async syncNow(): Promise<void> {
    if (this.running || !this.online()) {
      return;
    }
    this.running = true;
    this.syncing.set(true);
    try {
      await this.push();
      await this.pull();
      this.message.set('Sincronizado con el servidor');
    } catch (error) {
      console.error('Sync error', error);
      this.message.set('Sin conexión estable: se mantiene modo offline');
    } finally {
      this.running = false;
      this.syncing.set(false);
    }
  }

  async synchronize(): Promise<void> {
    await this.syncNow();
  }

  async terminal(): Promise<TerminalConfig | undefined> {
    return this.terminalConfig();
  }

  async flushPendingInventoryMovements(): Promise<void> {
    // Inventario se sincroniza vía endpoints dedicados; mantener compatibilidad.
    return;
  }

  async refreshPendingCount(): Promise<void> {
    this.pendingCount.set(await db.outbox.count());
  }

  async terminalConfig(): Promise<TerminalConfig | undefined> {
    return readConfig<TerminalConfig>(terminalConfigKey);
  }

  async securityPolicy(): Promise<SecurityPolicy | undefined> {
    return readConfig<SecurityPolicy>(securityPolicyKey);
  }

  private async applyPull(response: PullResponse): Promise<void> {
    await db.transaction('rw', db.products, db.customers, db.config, async () => {
      await db.products.clear();
      if (response.products.length > 0) {
        await db.products.bulkPut(response.products);
      }
      if (response.customers.length > 0) {
        await db.customers.bulkPut(response.customers);
      }
      await db.config.put({
        key: terminalConfigKey,
        value: response.terminal,
        updatedAt: response.serverTime,
      });
      await db.config.put({
        key: lastPullKey,
        value: response.serverTime,
        updatedAt: response.serverTime,
      });
      await db.config.put({
        key: sequenceKey(response.terminal.dianPrefix),
        value: response.terminal.lastSyncedSequence,
        updatedAt: response.serverTime,
      });
      if (response.securityPolicy) {
        await db.config.put({
          key: securityPolicyKey,
          value: response.securityPolicy,
          updatedAt: response.serverTime,
        });
      }
      if (response.supervisorPins) {
        await db.config.put({
          key: supervisorPinsKey,
          value: response.supervisorPins,
          updatedAt: response.serverTime,
        });
      }
    });
  }

  private async applyResult(result: SyncEventResult): Promise<void> {
    const event = await db.outbox.get(result.idempotencyKey);
    if (!event) {
      return;
    }

    if (result.status === 'PROCESSED' || result.status === 'DUPLICATE' || result.status === 'DUPLICATE_IGNORED') {
      await db.transaction('rw', db.outbox, db.salesHistory, async () => {
        await db.outbox.delete(result.idempotencyKey);
        await db.salesHistory.update(event.payload.id, {
          status: 'SYNCED',
          rejectionReason: undefined,
        });
      });
      return;
    }

    const reason = result.reason ?? 'Conflicto de sincronización';
    await db.transaction('rw', db.outbox, db.salesHistory, db.products, db.config, async () => {
      const history = await db.salesHistory.get(event.payload.id);
      if (history && history.status === 'OFFLINE_PENDING') {
        for (const item of history.items) {
          await db.products
            .where('id')
            .equals(item.productId)
            .modify((product) => {
              product.currentStock += item.quantity;
            });
        }

        const seqKey = sequenceKey(history.fullDocumentNumber.split('-')[0] ?? '');
        const stored = await db.config.get(seqKey);
        if (typeof stored?.value === 'number' && stored.value === history.sequenceNumber) {
          await db.config.put({
            key: seqKey,
            value: history.sequenceNumber - 1,
            updatedAt: new Date().toISOString(),
          });
        }
      }

      await db.salesHistory.update(event.payload.id, {
        status: 'REJECTED_CONFLICT',
        rejectionReason: reason,
      });
      await db.outbox.delete(result.idempotencyKey);
    });
  }

  private readonly onOnline = (): void => {
    this.online.set(true);
    this.message.set('Conexión detectada, sincronizando…');
    void this.syncNow();
  };

  private readonly onOffline = (): void => {
    this.online.set(false);
    this.message.set('Sin conexión: operando 100% local');
  };
}