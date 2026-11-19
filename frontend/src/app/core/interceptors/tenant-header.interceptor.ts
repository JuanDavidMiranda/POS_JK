import { HttpInterceptorFn } from '@angular/common/http';

/** Tenant demo mientras se integra JWT (siguiente paso del roadmap). */
export const DEMO_TENANT_ID = '11111111-1111-1111-1111-111111111111';

export const tenantHeaderInterceptor: HttpInterceptorFn = (request, next) => {
  return next(
    request.clone({
      setHeaders: { 'X-Tenant-Id': DEMO_TENANT_ID },
    }),
  );
};
