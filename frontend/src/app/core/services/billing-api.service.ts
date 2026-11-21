import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BillingPlan {
  code: string;
  name: string;
  monthlyPriceCop: number;
  annualPriceCop: number;
  maxBranches: number;
  maxTerminals: number;
  includedFoliosPerMonth: number;
  active: boolean;
}

export interface FolioPackage {
  code: string;
  name: string;
  foliosCount: number;
  priceCop: number;
}

export interface PaymentTransaction {
  id: string;
  referenceCode: string;
  gatewayTransactionId?: string | null;
  paymentType: string;
  itemCode: string;
  amountCop: number;
  status: string;
  paymentMethodType?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BillingSubscription {
  planCode: string;
  planName: string;
  subscriptionStatus: string;
  trialEndsAt?: string | null;
  subscriptionEndsAt?: string | null;
  electronicFoliosUsed: number;
  electronicFoliosLimit: number;
  electronicFoliosRemaining: number;
  daysRemaining: number;
  inGracePeriod: boolean;
  graceDaysRemaining: number;
}

export interface BillingOverview {
  subscription: BillingSubscription;
  plans: BillingPlan[];
  folioPackages: FolioPackage[];
  recentTransactions: PaymentTransaction[];
  wompiSandbox: boolean;
  wompiPublicKey: string;
}

export interface CheckoutIntentResponse {
  transactionId: string;
  publicKey: string;
  reference: string;
  amountInCents: number;
  currency: string;
  integritySignature: string;
  redirectUrl: string;
  sandbox: boolean;
  itemName: string;
  paymentType: string;
  itemCode: string;
}

@Injectable({ providedIn: 'root' })
export class BillingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/billing`;

  overview(): Promise<BillingOverview> {
    return firstValueFrom(this.http.get<BillingOverview>(`${this.base}/overview`));
  }

  checkoutIntent(paymentType: string, itemCode: string): Promise<CheckoutIntentResponse> {
    return firstValueFrom(this.http.post<CheckoutIntentResponse>(`${this.base}/checkout-intent`, { paymentType, itemCode }));
  }

  simulateSandbox(referenceCode: string, approve: boolean): Promise<PaymentTransaction> {
    return firstValueFrom(this.http.post<PaymentTransaction>(`${this.base}/sandbox/simulate`, { referenceCode, approve }));
  }
}
