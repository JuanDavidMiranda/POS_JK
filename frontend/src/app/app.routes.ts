import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'pos' },
  {
    path: 'pos',
    title: 'Punto de venta',
    loadComponent: () => import('./features/pos/pos').then((module) => module.Pos),
  },
  {
    path: 'caja',
    title: 'Caja',
    loadComponent: () => import('./features/cash-shift/cash-shift').then((module) => module.CashShift),
  },
  {
    path: 'historial',
    title: 'Historial de ventas',
    loadComponent: () => import('./features/history/history').then((module) => module.History),
  },
  {
    path: 'inventario',
    title: 'Inventario',
    loadComponent: () => import('./features/inventory/inventory').then((module) => module.Inventory),
  },
  {
    path: 'cargue-masivo',
    title: 'Cargue Masivo',
    loadComponent: () => import('./features/bulk-import/bulk-import').then((module) => module.BulkImport),
  },
  {
    path: 'seguridad',
    title: 'Seguridad',
    loadComponent: () => import('./features/security/security').then((module) => module.Security),
  },
  {
    path: 'reportes',
    title: 'Reportes',
    loadComponent: () => import('./features/reports/reports').then((module) => module.Reports),
  },
  { path: '**', redirectTo: 'pos' },
];
