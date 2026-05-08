import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: '/files' },
  {
    path: 'files',
    loadChildren: () => import('./pages/files/files.routes').then((m) => m.FILES_ROUTES),
  },

];
