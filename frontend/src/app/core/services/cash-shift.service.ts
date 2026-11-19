import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CashShiftResponse {
  id: string;
  terminalId?: string | null;
  openedBy?: string | null;
  closedBy?: string | null;
  authorizedByUserId?: string | null;
  status: 'OPEN' | 'CLOSED';
  openingAmount: number;
  expectedCashAmount?: number | null;
  countedCashAmount?: number | null;
  differenceAmount?: number | null;
  openedAt: string;
  closedAt?: string | null;
  notes?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CashShiftService {
  private readonly http = inject(HttpClient);

  open(request: { terminalId?: string | null; openingAmount: number; notes?: string }): Promise<CashShiftResponse> {
    return firstValueFrom(this.http.post<CashShiftResponse>(`${environment.apiBaseUrl}/api/v1/cash-shifts/open`, request));
  }

  close(shiftId: string, request: { authorizedByUserId?: string | null; countedCashAmount: number; notes?: string }): Promise<CashShiftResponse> {
    return firstValueFrom(this.http.post<CashShiftResponse>(`${environment.apiBaseUrl}/api/v1/cash-shifts/${shiftId}/close`, request));
  }

  current(): Promise<CashShiftResponse | null> {
    return firstValueFrom(this.http.get<CashShiftResponse | null>(`${environment.apiBaseUrl}/api/v1/cash-shifts/current`));
  }

  recent(): Promise<CashShiftResponse[]> {
    return firstValueFrom(this.http.get<CashShiftResponse[]>(`${environment.apiBaseUrl}/api/v1/cash-shifts`));
  }
}