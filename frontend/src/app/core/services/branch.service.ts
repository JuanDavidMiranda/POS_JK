import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Branch, TerminalInfo } from '../models/pos.models';

export interface BranchRequest {
  code: string;
  name: string;
  address?: string | null;
  city?: string | null;
  phone?: string | null;
  active?: boolean | null;
}

export interface TerminalRequest {
  branchId: string;
  name: string;
  dianPrefix: string;
  rangeFrom: number;
  rangeTo: number;
  creditNotePrefix?: string | null;
  active?: boolean | null;
}

@Injectable({ providedIn: 'root' })
export class BranchService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/branches`;

  listBranches(): Promise<Branch[]> {
    return firstValueFrom(this.http.get<Branch[]>(this.baseUrl));
  }

  createBranch(request: BranchRequest): Promise<Branch> {
    return firstValueFrom(this.http.post<Branch>(this.baseUrl, request));
  }

  updateBranch(id: string, request: BranchRequest): Promise<Branch> {
    return firstValueFrom(this.http.put<Branch>(`${this.baseUrl}/${id}`, request));
  }

  listTerminals(branchId?: string): Promise<TerminalInfo[]> {
    const params: Record<string, string> = {};
    if (branchId) params['branchId'] = branchId;
    return firstValueFrom(this.http.get<TerminalInfo[]>(`${this.baseUrl}/terminals`, { params }));
  }

  createTerminal(request: TerminalRequest): Promise<TerminalInfo> {
    return firstValueFrom(this.http.post<TerminalInfo>(`${this.baseUrl}/terminals`, request));
  }

  updateTerminal(id: string, request: TerminalRequest): Promise<TerminalInfo> {
    return firstValueFrom(this.http.put<TerminalInfo>(`${this.baseUrl}/terminals/${id}`, request));
  }
}

const STORAGE_KEY = 'pos.admin.selectedBranchId';

@Injectable({ providedIn: 'root' })
export class BranchContextService {
  readonly branches = signal<Branch[]>([]);
  readonly selectedBranchId = signal<string | null>(this.readStored());
  readonly loading = signal(false);

  readonly selectedBranch = computed(() => {
    const id = this.selectedBranchId();
    if (!id) return null;
    return this.branches().find((b) => b.id === id) ?? null;
  });

  readonly selectionLabel = computed(() => this.selectedBranch()?.name ?? 'Todas las sedes');

  setBranches(branches: Branch[]): void {
    this.branches.set(branches);
    const current = this.selectedBranchId();
    if (current && !branches.some((b) => b.id === current)) {
      this.setSelectedBranchId(null);
    }
  }

  setSelectedBranchId(id: string | null): void {
    this.selectedBranchId.set(id);
    try {
      if (id) localStorage.setItem(STORAGE_KEY, id);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
