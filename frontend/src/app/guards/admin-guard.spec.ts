import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';

import { adminGuard } from './admin-guard';
import { AuthService } from '../services/auth';
import { UserSummary } from '../models/rbac';

describe('adminGuard', () => {
  let authServiceStub: {
    currentUser: () => UserSummary | null;
    isAdmin: () => boolean;
    loadCurrentUser: ReturnType<typeof vi.fn>;
  };
  let routerStub: { createUrlTree: (commands: any[]) => UrlTree };

  beforeEach(() => {
    authServiceStub = {
      currentUser: () => ({ id: 1, username: 'admin', roles: [] }),
      isAdmin: () => true,
      loadCurrentUser: vi.fn().mockResolvedValue(undefined),
    };
    routerStub = { createUrlTree: (commands: any[]) => ({ commands }) as unknown as UrlTree };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });
  });

  it('allows navigation when the current user is already loaded and is an admin', async () => {
    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));

    expect(result).toBe(true);
    expect(authServiceStub.loadCurrentUser).not.toHaveBeenCalled();
  });

  it('redirects to /files when the current user is loaded but not an admin', async () => {
    authServiceStub.isAdmin = () => false;
    const spy = vi.spyOn(routerStub, 'createUrlTree');

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));

    expect(spy).toHaveBeenCalledWith(['/files']);
    expect(result).not.toBe(true);
  });

  it('loads the current user first when it has not been fetched yet, then allows an admin through', async () => {
    authServiceStub.currentUser = () => null;

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));

    expect(authServiceStub.loadCurrentUser).toHaveBeenCalled();
    expect(result).toBe(true);
  });

  it('redirects to /files when loading the current user reveals a non-admin', async () => {
    authServiceStub.currentUser = () => null;
    authServiceStub.isAdmin = () => false;
    const spy = vi.spyOn(routerStub, 'createUrlTree');

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));

    expect(authServiceStub.loadCurrentUser).toHaveBeenCalled();
    expect(spy).toHaveBeenCalledWith(['/files']);
    expect(result).not.toBe(true);
  });
});
