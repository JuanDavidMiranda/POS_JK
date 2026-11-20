import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Admin-only tenant pages (inventory mgmt, branches, security, reports, imports)
export const adminOnlyGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  if (auth.isSuperAdmin()) return router.createUrlTree(['/super-admin']);
  if (auth.user()?.role === 'ADMIN') return true;
  return router.createUrlTree(['/pos']);
};
