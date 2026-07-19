import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth';

export const adminGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.currentUser()) {
    await authService.loadCurrentUser();
  }

  if (authService.isAdmin()) {
    return true;
  }

  return router.createUrlTree(['/files']);
};
