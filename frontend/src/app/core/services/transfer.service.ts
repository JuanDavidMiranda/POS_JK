import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { InventoryTransfer, TransferStatus } from '../models/pos.models';
import type { PageResponse } from './inventory.service';

export interface CreateTransferItemRequest {
  productId: string;
  quantity: number;
}

export interface CreateTransferRequest {
  sourceBranchId: string;
  destinationBranchId: string;
  notes?: string | null;
  createdBy?: string | null;
  items: CreateTransferItemRequest[];
}

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/inventory/transfers`;

  list(params?: {
    sourceBranchId?: string | null;
    destinationBranchId?: string | null;
    status?: TransferStatus | null;
    page?: number;
    size?: number;
  }): Promise<PageResponse<InventoryTransfer>> {
    const httpParams: Record<string, string> = {
      page: String(params?.page ?? 0),
      size: String(params?.size ?? 20),
    };
    if (params?.sourceBranchId) httpParams['sourceBranchId'] = params.sourceBranchId;
    if (params?.destinationBranchId) httpParams['destinationBranchId'] = params.destinationBranchId;
    if (params?.status) httpParams['status'] = params.status;
    return firstValueFrom(this.http.get<PageResponse<InventoryTransfer>>(this.baseUrl, { params: httpParams }));
  }

  get(id: string): Promise<InventoryTransfer> {
    return firstValueFrom(this.http.get<InventoryTransfer>(`${this.baseUrl}/${id}`));
  }

  create(request: CreateTransferRequest): Promise<InventoryTransfer> {
    return firstValueFrom(this.http.post<InventoryTransfer>(this.baseUrl, request));
  }

  receive(id: string, receivedBy?: string | null): Promise<InventoryTransfer> {
    return firstValueFrom(
      this.http.post<InventoryTransfer>(`${this.baseUrl}/${id}/receive`, receivedBy ? { receivedBy } : {}),
    );
  }

  cancel(id: string, reason?: string | null): Promise<InventoryTransfer> {
    return firstValueFrom(
      this.http.post<InventoryTransfer>(`${this.baseUrl}/${id}/cancel`, reason ? { reason } : {}),
    );
  }
}
