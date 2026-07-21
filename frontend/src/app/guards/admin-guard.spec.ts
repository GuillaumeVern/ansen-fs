import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';

import { adminGuard } from './admin-guard';
import { AuthService } from '../services/auth';

describe('adminGuard', () => {
  let authServiceStub: { isAdmin: () => boolean };
  let routerStub: { createUrlTree: (commands: any[]) => UrlTree };

  beforeEach(() => {
    authServiceStub = { isAdmin: () => true };
    routerStub = { createUrlTree: (commands: any[]) => ({ commands }) as unknown as UrlTree };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });
  });

  it('allows navigation when the current user is an admin', () => {
    const result = TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));
    expect(result).toBe(true);
  });

  it('redirects to /files when the current user is not an admin', () => {
    authServiceStub.isAdmin = () => false;
    const spy = vi.spyOn(routerStub, 'createUrlTree');

    const result = TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));

    expect(spy).toHaveBeenCalledWith(['/files']);
    expect(result).not.toBe(true);
  });
});
