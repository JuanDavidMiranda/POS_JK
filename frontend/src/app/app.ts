import { ChangeDetectionStrategy, Component, OnInit, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SyncService } from './core/services/sync.service';
import { BranchContextService, BranchService } from './core/services/branch.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  readonly sync = inject(SyncService);
  readonly branchCtx = inject(BranchContextService);
  private readonly branchService = inject(BranchService);

  constructor() {
    // refresh branches when sync finishes (and on init)
    effect(() => {
      const at = this.sync.lastSyncAt();
      if (at) void this.loadBranches();
    });
  }

  ngOnInit(): void {
    void this.sync.init();
    void this.loadBranches();
  }

  async loadBranches(): Promise<void> {
    if (!this.sync.online()) return;
    try {
      const branches = await this.branchService.listBranches();
      this.branchCtx.setBranches(branches);
    } catch {
      // ignore (offline or no admin access)
    }
  }

  onBranchChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.branchCtx.setSelectedBranchId(value || null);
  }
}
