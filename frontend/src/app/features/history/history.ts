import { ChangeDetectionStrategy, Component, OnInit, effect, inject, signal } from '@angular/core';
import { db } from '../../core/db/pos-offline.database';
import { money, type SaleHistoryRecord } from '../../core/models/pos.models';
import { SyncService } from '../../core/services/sync.service';

@Component({
  selector: 'app-history',
  templateUrl: './history.html',
  styleUrl: './history.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class History implements OnInit {
  readonly sync = inject(SyncService);
  readonly sales = signal<SaleHistoryRecord[]>([]);
  readonly money = money;

  constructor() {
    effect(() => {
      if (this.sync.lastSyncAt()) {
        void this.reload();
      }
    });
  }

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.sales.set(await db.salesHistory.orderBy('soldAt').reverse().toArray());
  }

  async refresh(): Promise<void> {
    await this.sync.synchronize();
    await this.reload();
  }

  formatDate(iso: string): string {
    return new Intl.DateTimeFormat('es-CO', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(iso));
  }
}
