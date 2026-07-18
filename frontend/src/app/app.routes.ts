import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: '/files' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'files',
    canActivate: [authGuard],
    loadChildren: () => import('./pages/files/files.routes').then((m) => m.FILES_ROUTES),
  },

];
