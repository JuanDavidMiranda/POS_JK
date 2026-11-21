import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AccountingConfig {
  targetSoftware: string;
  salesIncomeAccount: string;
  vatGeneratedAccount: string;
  incGeneratedAccount: string;
  cashAccount: string;
  bankTransferAccount: string;
  returnsAccount: string;
}

export interface AccountingRow {
  date: string;
  documentType: string;
  documentNumber: string;
  thirdParty: string;
  concept: string;
  accountCode: string;
  accountName: string;
  debit: number;
  credit: number;
  paymentMethod: string;
}

export interface AccountingPreview {
  format: string;
  from: string;
  to: string;
  rows: AccountingRow[];
  totalRows: number;
}

@Injectable({ providedIn: 'root' })
export class AccountingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/accounting`;

  getConfig(): Promise<AccountingConfig> {
    return firstValueFrom(this.http.get<AccountingConfig>(`${this.base}/config`));
  }

  updateConfig(config: AccountingConfig): Promise<AccountingConfig> {
    return firstValueFrom(this.http.put<AccountingConfig>(`${this.base}/config`, config));
  }

  preview(from: string, to: string, format: string): Promise<AccountingPreview> {
    return firstValueFrom(this.http.get<AccountingPreview>(`${this.base}/preview`, { params: { from, to, format } }));
  }

  async export(from: string, to: string, format: string): Promise<void> {
    const blob = await firstValueFrom(this.http.get(`${this.base}/export`, {
      params: { from, to, format },
      responseType: 'blob',
    }));
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `export-contable-${format.toLowerCase()}-${from}-${to}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
