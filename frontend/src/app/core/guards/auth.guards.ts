import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) return true;
  return router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return true;
  if (auth.isSuperAdmin()) return router.createUrlTree(['/super-admin']);
  if (auth.needsOnboarding()) return router.createUrlTree(['/onboarding']);
  return router.createUrlTree(['/pos']);
};

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated() && auth.isSuperAdmin()) return true;
  if (auth.isAuthenticated()) return router.createUrlTree(['/pos']);
  return router.createUrlTree(['/login']);
};

export const tenantUserGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  if (auth.isSuperAdmin()) return router.createUrlTree(['/super-admin']);
  return true;
};

export const onboardingGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  if (auth.isSuperAdmin()) return router.createUrlTree(['/super-admin']);
  const u = auth.user();
  if (u?.role !== 'ADMIN') return router.createUrlTree(['/pos']);
  const t = auth.tenant();
  if (t?.onboardingCompleted) return router.createUrlTree(['/pos']);
  return true;
};

// For tenant app routes: if admin and onboarding incomplete (and not deferred), send to onboarding
export const onboardingRequiredGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  if (auth.isSuperAdmin()) return router.createUrlTree(['/super-admin']);
  if (auth.needsOnboarding()) return router.createUrlTree(['/onboarding']);
  return true;
};
