import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { SaleSummaryResponse, TaxSummaryResponse, TopProductsResponse } from '../models/pos.models';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  salesSummary(from: string, to: string): Promise<SaleSummaryResponse> {
    return firstValueFrom(this.http.get<SaleSummaryResponse>(`${environment.apiBaseUrl}/api/v1/reports/sales-summary`, {
      params: { from, to },
    }));
  }

  topProducts(from: string, to: string, limit = 20): Promise<TopProductsResponse> {
    return firstValueFrom(this.http.get<TopProductsResponse>(`${environment.apiBaseUrl}/api/v1/reports/top-products`, {
      params: { from, to, limit: String(limit) },
    }));
  }

  taxSummary(from: string, to: string): Promise<TaxSummaryResponse> {
    return firstValueFrom(this.http.get<TaxSummaryResponse>(`${environment.apiBaseUrl}/api/v1/reports/tax-summary`, {
      params: { from, to },
    }));
  }

  async downloadTaxSummaryExcel(from: string, to: string): Promise<void> {
    const blob = await firstValueFrom(this.http.get(`${environment.apiBaseUrl}/api/v1/reports/tax-summary/export`, {
      params: { from, to },
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