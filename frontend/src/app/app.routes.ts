import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';
import { adminGuard } from './guards/admin-guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: '/files' },
  {
    path: 'login',
    title: 'Login - AnzenFS',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'files',
    canActivate: [authGuard],
    loadChildren: () => import('./pages/files/files.routes').then((m) => m.FILES_ROUTES),
  },
  {
    path: 'bin',
    canActivate: [authGuard],
    loadChildren: () => import('./pages/trash/trash.routes').then((m) => m.TRASH_ROUTES),
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadChildren: () => import('./pages/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },

];
