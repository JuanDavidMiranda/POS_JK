import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { SecurityPolicy } from '../models/pos.models';

export interface UserSummary {
  id: string;
  username: string;
  fullName: string;
  role: string;
  active: boolean;
  hasSupervisorPin: boolean;
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class SecurityService {
  private readonly http = inject(HttpClient);

  getPolicies(): Promise<SecurityPolicy> {
    return firstValueFrom(this.http.get<SecurityPolicy>(`${environment.apiBaseUrl}/api/v1/security/policies`));
  }

  updatePolicies(policy: SecurityPolicy): Promise<SecurityPolicy> {
    return firstValueFrom(this.http.put<SecurityPolicy>(`${environment.apiBaseUrl}/api/v1/security/policies`, {
      requirePinForDiscounts: policy.requirePinForDiscounts,
      maxCashierDiscountPercent: policy.maxCashierDiscountPercent,
      requirePinForVoidItem: policy.requirePinForVoidItem,
      blindCashClose: policy.blindCashClose,
    }));
  }

  listUsers(): Promise<UserSummary[]> {
    return firstValueFrom(this.http.get<UserSummary[]>(`${environment.apiBaseUrl}/api/v1/users`));
  }

  setPin(userId: string, pin: string): Promise<{ userId: string; updated: boolean }> {
    return firstValueFrom(this.http.put<{ userId: string; updated: boolean }>(
      `${environment.apiBaseUrl}/api/v1/users/${userId}/pin`, { pin }));
  }
}