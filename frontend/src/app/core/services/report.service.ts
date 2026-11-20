import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { SaleSummaryResponse, TaxSummaryResponse, TopProductsResponse } from '../models/pos.models';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  private params(from: string, to: string, branchId?: string | null, extra?: Record<string, string>): Record<string, string> {
    const p: Record<string, string> = { from, to, ...(extra ?? {}) };
    if (branchId) p['branchId'] = branchId;
    return p;
  }

  salesSummary(from: string, to: string, branchId?: string | null): Promise<SaleSummaryResponse> {
    return firstValueFrom(this.http.get<SaleSummaryResponse>(`${environment.apiBaseUrl}/api/v1/reports/sales-summary`, {
      params: this.params(from, to, branchId),
    }));
  }

  topProducts(from: string, to: string, limit = 20, branchId?: string | null): Promise<TopProductsResponse> {
    return firstValueFrom(this.http.get<TopProductsResponse>(`${environment.apiBaseUrl}/api/v1/reports/top-products`, {
      params: this.params(from, to, branchId, { limit: String(limit) }),
    }));
  }

  taxSummary(from: string, to: string, branchId?: string | null): Promise<TaxSummaryResponse> {
    return firstValueFrom(this.http.get<TaxSummaryResponse>(`${environment.apiBaseUrl}/api/v1/reports/tax-summary`, {
      params: this.params(from, to, branchId),
    }));
  }

  async downloadTaxSummaryExcel(from: string, to: string, branchId?: string | null): Promise<void> {
    const blob = await firstValueFrom(this.http.get(`${environment.apiBaseUrl}/api/v1/reports/tax-summary/export`, {
      params: this.params(from, to, branchId),
      responseType: 'blob',
    }));
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `informe-tributario_${from}_${to}.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
}
