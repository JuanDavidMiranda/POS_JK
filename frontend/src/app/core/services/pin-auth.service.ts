import { Injectable, inject } from '@angular/core';
import { readConfig } from '../db/pos-offline.database';
import { AuthService } from './auth.service';
import { securityPolicyKey, supervisorPinsKey } from '../db/pos-offline.database';
import type { SecurityPolicy, SupervisorUser } from '../models/pos.models';

export interface PinAuthorizationResult {
  authorizedBy: string;
  authorizedName: string;
}

@Injectable({ providedIn: 'root' })
export class PinAuthService {
  private readonly auth = inject(AuthService);

  async getPolicy(): Promise<SecurityPolicy> {
    const policy = await readConfig<SecurityPolicy>(securityPolicyKey);
    return policy ?? {
      requirePinForDiscounts: true,
      maxCashierDiscountPercent: 0,
      requirePinForVoidItem: true,
      blindCashClose: true,
    };
  }

  async getSupervisors(): Promise<SupervisorUser[]> {
    return (await readConfig<SupervisorUser[]>(supervisorPinsKey)) ?? [];
  }

  async authorize(pin: string): Promise<PinAuthorizationResult | null> {
    const supervisors = await this.getSupervisors();
    if (!supervisors.length || !pin) {
      return null;
    }
    const tenantId = this.auth.tenant()?.id;
    if (!tenantId) return null;
    const hash = await this.hashPin(tenantId, pin);
    const match = supervisors.find((s) => s.pinHash === hash);
    if (!match) {
      return null;
    }
    return { authorizedBy: match.userId, authorizedName: match.fullName };
  }

  async hashPin(tenantId: string, pin: string): Promise<string> {
    const data = new TextEncoder().encode(`${tenantId}:${pin}`);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, '0')).join('');
  }
}