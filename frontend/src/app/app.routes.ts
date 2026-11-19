import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'pos' },
  {
    path: 'pos',
    title: 'Punto de venta',
    loadComponent: () => import('./features/pos/pos').then((module) => module.Pos),
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
  { path: '**', redirectTo: 'pos' },
];
