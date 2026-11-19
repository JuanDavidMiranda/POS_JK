import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ImportEntityType = 'PRODUCTS' | 'CUSTOMERS';

export interface ImportRowError {
  rowNumber: number;
  identifier: string;
  rawValues: Record<string, string | null | undefined>;
  errorMessage: string;
}

export interface ImportRowPreview {
  rowNumber: number;
  identifier: string;
  values: Record<string, string | number | null | undefined>;
}

export interface ImportPreviewResponse {
  totalRows: number;
  validRowsCount: number;
  invalidRowsCount: number;
  previewSample: ImportRowPreview[];
  errors: ImportRowError[];
}

export interface ImportExecuteResponse {
  jobId: string;
}

export interface ImportJobResponse {
  jobId: string;
  entityType: ImportEntityType;
  fileName: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  totalRows: number;
  processedRows: number;
  createdCount: number;
  updatedCount: number;
  rejectedCount: number;
  hasRejectedFile: boolean;
  createdAt: string;
  finishedAt?: string | null;
}

@Injectable({ providedIn: 'root' })
export class BulkImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/imports`;

  async downloadTemplate(entityType: ImportEntityType): Promise<void> {
    const blob = await firstValueFrom(
      this.http.get(`${this.baseUrl}/template/${entityType}`, { responseType: 'blob' }),
    );
    this.saveBlob(blob, entityType === 'PRODUCTS' ? 'plantilla_productos.xlsx' : 'plantilla_clientes.xlsx');
  }

  preview(entityType: ImportEntityType, file: File): Promise<ImportPreviewResponse> {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(
      this.http.post<ImportPreviewResponse>(`${this.baseUrl}/preview/${entityType}`, form),
    );
  }

  execute(entityType: ImportEntityType, file: File): Promise<ImportExecuteResponse> {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(
      this.http.post<ImportExecuteResponse>(`${this.baseUrl}/execute/${entityType}`, form),
    );
  }

  getJob(jobId: string): Promise<ImportJobResponse> {
    return firstValueFrom(this.http.get<ImportJobResponse>(`${this.baseUrl}/jobs/${jobId}`));
  }

  async downloadRejected(jobId: string, fileName = 'rechazados.xlsx'): Promise<void> {
    const response = await firstValueFrom(
      this.http.get(`${this.baseUrl}/jobs/${jobId}/rejected-file`, {
        responseType: 'blob',
        observe: 'response',
      }),
    );
    const name = this.fileNameFromDisposition(response) ?? fileName;
    if (response.body) {
      this.saveBlob(response.body, name);
    }
  }

  history(): Promise<ImportJobResponse[]> {
    return firstValueFrom(this.http.get<ImportJobResponse[]>(`${this.baseUrl}/history`));
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private fileNameFromDisposition(response: HttpResponse<Blob>): string | null {
    const disposition = response.headers.get('Content-Disposition');
    if (!disposition) {
      return null;
    }
    const match = /filename\*?=(?:UTF-8''|"?)([^";]+)/i.exec(disposition);
    if (!match?.[1]) {
      return null;
    }
    try {
      return decodeURIComponent(match[1].replace(/"/g, '').trim());
    } catch {
      return match[1].replace(/"/g, '').trim();
    }
  }
}
