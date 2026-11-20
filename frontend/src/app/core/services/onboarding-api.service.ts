import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { DianStepRequest, DianStepResponse, FiscalStepRequest, OnboardingStatusResponse, SeedCatalogRequest, SeedCatalogResponse } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class OnboardingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/onboarding`;

  status(): Promise<OnboardingStatusResponse> {
    return firstValueFrom(this.http.get<OnboardingStatusResponse>(`${this.base}/status`));
  }
  saveFiscal(req: FiscalStepRequest): Promise<OnboardingStatusResponse> {
    return firstValueFrom(this.http.put<OnboardingStatusResponse>(`${this.base}/step-1/fiscal`, req));
  }
  saveDian(req: DianStepRequest): Promise<DianStepResponse> {
    return firstValueFrom(this.http.put<DianStepResponse>(`${this.base}/step-2/dian`, req));
  }
  seedCatalog(req: SeedCatalogRequest): Promise<SeedCatalogResponse> {
    return firstValueFrom(this.http.post<SeedCatalogResponse>(`${this.base}/step-3/seed-catalog`, req));
  }
}
