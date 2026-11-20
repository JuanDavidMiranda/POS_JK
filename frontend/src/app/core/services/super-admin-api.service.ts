import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { SuperAdminDashboard, SuperAdminSubscriptionUpdate, SuperAdminTenantHealth, SuperAdminTenantItem, SuperAdminTenantListResponse } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class SuperAdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/super-admin`;

  dashboard(): Promise<SuperAdminDashboard> {
    return firstValueFrom(this.http.get<SuperAdminDashboard>(`${this.base}/dashboard`));
  }

  tenants(q = '', status = 'ALL', page = 0, size = 20): Promise<SuperAdminTenantListResponse> {
    let params = new HttpParams().set('status', status).set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    return firstValueFrom(this.http.get<SuperAdminTenantListResponse>(`${this.base}/tenants`, { params }));
  }

  updateSubscription(tenantId: string, req: SuperAdminSubscriptionUpdate): Promise<SuperAdminTenantItem> {
    return firstValueFrom(this.http.patch<SuperAdminTenantItem>(`${this.base}/tenants/${tenantId}/subscription`, req));
  }

  health(tenantId: string): Promise<SuperAdminTenantHealth> {
    return firstValueFrom(this.http.get<SuperAdminTenantHealth>(`${this.base}/tenants/${tenantId}/health`));
  }
}
