import { Routes } from '@angular/router';
import { adminOnlyGuard } from './core/guards/admin.guard';
import { authGuard, guestGuard, onboardingGuard, onboardingRequiredGuard, superAdminGuard, tenantUserGuard } from './core/guards/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Iniciar sesión',
    loadComponent: () => import('./features/auth/login/login').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Registro',
    loadComponent: () => import('./features/auth/register/register').then(m => m.RegisterComponent),
  },
  {
    path: 'onboarding',
    canActivate: [onboardingGuard],
    title: 'Configuración inicial',
    loadComponent: () => import('./features/onboarding/onboarding').then(m => m.OnboardingComponent),
  },
  {
    path: 'super-admin',
    canActivate: [superAdminGuard],
    title: 'Panel Super Admin',
    loadComponent: () => import('./features/super-admin/super-admin').then(m => m.SuperAdminComponent),
  },
  {
    path: '',
    canActivate: [tenantUserGuard, onboardingRequiredGuard],
    loadComponent: () => import('./features/shell/tenant-shell').then(m => m.TenantShell),
    children: [
      {
        path: 'pos',
        title: 'Punto de venta',
        loadComponent: () => import('./features/pos/pos').then(m => m.Pos),
      },
      {
        path: 'caja',
        title: 'Caja',
        loadComponent: () => import('./features/cash-shift/cash-shift').then(m => m.CashShift),
      },
      {
        path: 'historial',
        title: 'Historial de ventas',
        loadComponent: () => import('./features/history/history').then(m => m.History),
      },
      {
        path: 'inventario',
        title: 'Inventario',
        loadComponent: () => import('./features/inventory/inventory').then(m => m.Inventory),
      },
      {
        path: 'traslados',
        canActivate: [adminOnlyGuard],
        title: 'Traslados entre sedes',
        loadComponent: () => import('./features/transfers/transfers').then(m => m.Transfers),
      },
      {
        path: 'sedes',
        canActivate: [adminOnlyGuard],
        title: 'Sedes y cajas',
        loadComponent: () => import('./features/branches/branches').then(m => m.Branches),
      },
      {
        path: 'cargue-masivo',
        canActivate: [adminOnlyGuard],
        title: 'Cargue Masivo',
        loadComponent: () => import('./features/bulk-import/bulk-import').then(m => m.BulkImport),
      },
      {
        path: 'seguridad',
        canActivate: [adminOnlyGuard],
        title: 'Seguridad',
        loadComponent: () => import('./features/security/security').then(m => m.Security),
      },
      {
        path: 'reportes',
        canActivate: [adminOnlyGuard],
        title: 'Reportes',
        loadComponent: () => import('./features/reports/reports').then(m => m.Reports),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
