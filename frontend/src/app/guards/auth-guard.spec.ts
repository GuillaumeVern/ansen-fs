import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';

import { authGuard } from './auth-guard';
import { AuthService } from '../services/auth';

describe('authGuard', () => {
  let authServiceStub: { isAuthenticated: () => boolean };
  let routerStub: { createUrlTree: (commands: any[]) => UrlTree };

  beforeEach(() => {
    authServiceStub = { isAuthenticated: () => true };
    routerStub = { createUrlTree: (commands: any[]) => ({ commands }) as unknown as UrlTree };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });
  });

  it('allows navigation when the user is authenticated', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    expect(result).toBe(true);
  });

  it('redirects to /login when the user is not authenticated', () => {
    authServiceStub.isAuthenticated = () => false;
    const spy = vi.spyOn(routerStub, 'createUrlTree');

    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(spy).toHaveBeenCalledWith(['/login']);
    expect(result).not.toBe(true);
  });
});
