import { HttpInterceptorFn } from '@angular/common/http';

const TOKEN_KEY = 'pos.auth.token';

export function getStoredToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setStoredToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* ignore */ }
}

export const tenantHeaderInterceptor: HttpInterceptorFn = (request, next) => {
  const token = getStoredToken();
  if (token) {
    return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(request);
};
