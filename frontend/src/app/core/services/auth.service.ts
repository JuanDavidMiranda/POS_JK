import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { getStoredToken, setStoredToken } from '../interceptors/tenant-header.interceptor';
import type { AuthResponse, AuthTenant, AuthUser, LoginRequest, MeResponse, RegisterRequest } from '../models/auth.models';

const USER_KEY = 'pos.auth.user';
const TENANT_KEY = 'pos.auth.tenant';
const ONBOARDING_DEFER_KEY = 'pos.onboarding.deferred';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base = `${environment.apiBaseUrl}/api/v1`;

  readonly user = signal<AuthUser | null>(this.readJson<AuthUser>(USER_KEY));
  readonly tenant = signal<AuthTenant | null>(this.readJson<AuthTenant>(TENANT_KEY));
  readonly token = signal<string | null>(getStoredToken());
  readonly ready = signal(false);

  readonly isAuthenticated = computed(() => !!this.token() && !!this.user());
  readonly isSuperAdmin = computed(() => this.user()?.role === 'SUPER_ADMIN');
  readonly isTenantAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly needsOnboarding = computed(() => {
    const u = this.user();
    const t = this.tenant();
    if (!u || u.role !== 'ADMIN' || !t) return false;
    if (t.onboardingCompleted) return false;
    return !this.isOnboardingDeferred();
  });

  async init(): Promise<void> {
    const token = this.token();
    if (!token) {
      this.ready.set(true);
      return;
    }
    try {
      const me = await firstValueFrom(this.http.get<MeResponse>(`${this.base}/auth/me`));
      this.user.set(me.user);
      this.tenant.set(me.tenant);
      this.writeJson(USER_KEY, me.user);
      this.writeJson(TENANT_KEY, me.tenant);
    } catch {
      this.clearSession();
    } finally {
      this.ready.set(true);
    }
  }

  async login(req: LoginRequest): Promise<AuthResponse> {
    const res = await firstValueFrom(this.http.post<AuthResponse>(`${this.base}/auth/login`, req));
    this.applyAuth(res);
    return res;
  }

  async register(req: RegisterRequest): Promise<AuthResponse> {
    const res = await firstValueFrom(this.http.post<AuthResponse>(`${this.base}/public/register`, req));
    this.applyAuth(res);
    return res;
  }

  applyAuth(res: AuthResponse): void {
    setStoredToken(res.token);
    this.token.set(res.token);
    this.user.set(res.user);
    this.tenant.set(res.tenant);
    this.writeJson(USER_KEY, res.user);
    this.writeJson(TENANT_KEY, res.tenant);
    if (res.tenant?.onboardingCompleted) this.setOnboardingDeferred(false);
  }

  updateTenant(tenant: AuthTenant | null): void {
    this.tenant.set(tenant);
    this.writeJson(TENANT_KEY, tenant);
  }

  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  deferOnboarding(): void { this.setOnboardingDeferred(true); }
  clearOnboardingDeferral(): void { this.setOnboardingDeferred(false); }
  isOnboardingDeferred(): boolean {
    try { return sessionStorage.getItem(ONBOARDING_DEFER_KEY) === '1'; } catch { return false; }
  }
  private setOnboardingDeferred(v: boolean): void {
    try {
      if (v) sessionStorage.setItem(ONBOARDING_DEFER_KEY, '1');
      else sessionStorage.removeItem(ONBOARDING_DEFER_KEY);
    } catch { /* ignore */ }
  }

  private clearSession(): void {
    setStoredToken(null);
    this.token.set(null);
    this.user.set(null);
    this.tenant.set(null);
    try { localStorage.removeItem(USER_KEY); localStorage.removeItem(TENANT_KEY); } catch {}
  }

  private readJson<T>(key: string): T | null {
    try { const v = localStorage.getItem(key); return v ? JSON.parse(v) as T : null; } catch { return null; }
  }
  private writeJson(key: string, value: unknown): void {
    try {
      if (value == null) localStorage.removeItem(key);
      else localStorage.setItem(key, JSON.stringify(value));
    } catch {}
  }
}
