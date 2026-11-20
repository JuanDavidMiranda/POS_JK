import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BranchService, BranchContextService, type BranchRequest, type TerminalRequest } from '../../core/services/branch.service';
import type { Branch, TerminalInfo } from '../../core/models/pos.models';

@Component({
  selector: 'app-branches',
  imports: [FormsModule],
  templateUrl: './branches.html',
  styleUrl: './branches.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Branches implements OnInit {
  private readonly branchService = inject(BranchService);
  readonly branchCtx = inject(BranchContextService);

  readonly branches = signal<Branch[]>([]);
  readonly terminals = signal<TerminalInfo[]>([]);
  readonly loading = signal(false);
  readonly savingBranch = signal(false);
  readonly savingTerminal = signal(false);
  readonly error = signal('');
  readonly feedback = signal('');

  readonly editingBranchId = signal<string | null>(null);
  readonly branchForm = signal<BranchRequest>({ code: '', name: '', address: '', city: '', phone: '', active: true });

  readonly editingTerminalId = signal<string | null>(null);
  readonly terminalForm = signal<TerminalRequest>({
    branchId: '', name: '', dianPrefix: 'POS', rangeFrom: 1, rangeTo: 100000, creditNotePrefix: 'NC', active: true,
  });

  readonly terminalFilterBranchId = signal<string>('');

  readonly filteredTerminals = computed(() => {
    const bid = this.terminalFilterBranchId();
    const list = this.terminals();
    return bid ? list.filter((t) => t.branchId === bid) : list;
  });

  async ngOnInit(): Promise<void> {
    await this.loadAll();
  }

  async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [branches, terminals] = await Promise.all([
        this.branchService.listBranches(),
        this.branchService.listTerminals(),
      ]);
      this.branches.set(branches);
      this.branchCtx.setBranches(branches);
      this.terminals.set(terminals);
      if (!this.terminalForm().branchId && branches.length) {
        this.terminalForm.update((f) => ({ ...f, branchId: branches[0].id }));
      }
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible cargar sedes y cajas'));
    } finally {
      this.loading.set(false);
    }
  }

  newBranch(): void {
    this.editingBranchId.set(null);
    this.branchForm.set({ code: '', name: '', address: '', city: '', phone: '', active: true });
  }

  editBranch(b: Branch): void {
    this.editingBranchId.set(b.id);
    this.branchForm.set({ code: b.code, name: b.name, address: b.address ?? '', city: b.city ?? '', phone: b.phone ?? '', active: b.active });
  }

  async saveBranch(): Promise<void> {
    const f = this.branchForm();
    if (!f.code?.trim() || !f.name?.trim()) { this.error.set('Código y nombre de la sede son obligatorios'); return; }
    this.savingBranch.set(true); this.error.set(''); this.feedback.set('');
    try {
      const req: BranchRequest = {
        code: f.code.trim(), name: f.name.trim(),
        address: f.address?.trim() || null, city: f.city?.trim() || null, phone: f.phone?.trim() || null,
        active: f.active ?? true,
      };
      const id = this.editingBranchId();
      if (id) await this.branchService.updateBranch(id, req);
      else await this.branchService.createBranch(req);
      this.feedback.set(id ? 'Sede actualizada.' : 'Sede creada. Se inicializó el inventario para los productos activos.');
      this.newBranch();
      await this.loadAll();
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible guardar la sede'));
    } finally { this.savingBranch.set(false); }
  }

  newTerminal(): void {
    this.editingTerminalId.set(null);
    const defaultBranch = this.branches()[0]?.id ?? '';
    this.terminalForm.set({ branchId: defaultBranch, name: '', dianPrefix: 'POS', rangeFrom: 1, rangeTo: 100000, creditNotePrefix: 'NC', active: true });
  }

  editTerminal(t: TerminalInfo): void {
    this.editingTerminalId.set(t.id);
    this.terminalForm.set({
      branchId: t.branchId, name: t.name, dianPrefix: t.dianPrefix,
      rangeFrom: t.rangeFrom, rangeTo: t.rangeTo, creditNotePrefix: t.creditNotePrefix, active: t.active,
    });
  }

  async saveTerminal(): Promise<void> {
    const f = this.terminalForm();
    if (!f.branchId) { this.error.set('La sede es obligatoria para la caja/terminal'); return; }
    if (!f.name?.trim() || !f.dianPrefix?.trim()) { this.error.set('Nombre y prefijo DIAN son obligatorios'); return; }
    if (!(f.rangeFrom >= 1) || !(f.rangeTo >= f.rangeFrom)) { this.error.set('El rango DIAN es inválido'); return; }
    this.savingTerminal.set(true); this.error.set(''); this.feedback.set('');
    try {
      const req: TerminalRequest = {
        branchId: f.branchId, name: f.name.trim(), dianPrefix: f.dianPrefix.trim().toUpperCase(),
        rangeFrom: Number(f.rangeFrom), rangeTo: Number(f.rangeTo),
        creditNotePrefix: f.creditNotePrefix?.trim() || null, active: f.active ?? true,
      };
      const id = this.editingTerminalId();
      if (id) await this.branchService.updateTerminal(id, req);
      else await this.branchService.createTerminal(req);
      this.feedback.set(id ? 'Caja/terminal actualizada.' : 'Caja/terminal creada.');
      this.newTerminal();
      await this.loadAll();
    } catch (e: any) {
      this.error.set(this.msg(e, 'No fue posible guardar la caja/terminal'));
    } finally { this.savingTerminal.set(false); }
  }

  setTerminalFilter(value: string): void { this.terminalFilterBranchId.set(value); }

  updateBranchField<K extends keyof BranchRequest>(key: K, value: BranchRequest[K]): void {
    this.branchForm.update((f) => ({ ...f, [key]: value }));
  }
  updateTerminalField<K extends keyof TerminalRequest>(key: K, value: TerminalRequest[K]): void {
    this.terminalForm.update((f) => ({ ...f, [key]: value }));
  }

  private msg(e: any, fallback: string): string {
    return e?.error?.message || e?.error?.detail || e?.message || fallback;
  }
}
