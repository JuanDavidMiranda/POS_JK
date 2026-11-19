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
  { path: '**', redirectTo: 'pos' },
];
