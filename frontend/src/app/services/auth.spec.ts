import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { AuthService } from './auth';
import { StoreService } from './store';
import { UserSummary } from '../models/rbac';

const TOKEN_STORAGE_KEY = 'anzenfs.token';

const adminSummary: UserSummary = {
  id: 1,
  username: 'admin',
  roles: [{ id: 1, name: 'ADMIN', permissions: [] }],
};

const plainUserSummary: UserSummary = {
  id: 2,
  username: 'alice',
  roles: [{ id: 2, name: 'USER_ROLE', permissions: [] }],
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let storeService: StoreService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    storeService = TestBed.inject(StoreService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts unauthenticated when no token is stored', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token).toBeNull();
    expect(service.currentUser).toBeNull();
    expect(service.isAdmin()).toBe(false);
  });

  it('picks up a pre-existing token from localStorage without fetching the user until init() runs', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'preexisting-token');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    const fresh = TestBed.inject(AuthService);
    expect(fresh.token).toBe('preexisting-token');
    expect(fresh.currentUser).toBeNull();

    TestBed.inject(HttpTestingController).verify();
  });

  describe('init', () => {
    it('loads the current user when a token is already present', async () => {
      localStorage.setItem(TOKEN_STORAGE_KEY, 'preexisting-token');
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });

      const fresh = TestBed.inject(AuthService);
      const freshHttpMock = TestBed.inject(HttpTestingController);

      const initPromise = fresh.init();
      freshHttpMock.expectOne('/api/auth/me').flush(adminSummary);
      await initPromise;

      expect(fresh.currentUser).toEqual(adminSummary);
      expect(fresh.isAdmin()).toBe(true);
      freshHttpMock.verify();
    });

    it('does nothing when there is no token', async () => {
      await service.init();
      expect(service.currentUser).toBeNull();
    });

    it('clears currentUser when the request fails', async () => {
      localStorage.setItem(TOKEN_STORAGE_KEY, 'preexisting-token');
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });

      const fresh = TestBed.inject(AuthService);
      const freshHttpMock = TestBed.inject(HttpTestingController);

      const initPromise = fresh.init();
      freshHttpMock.expectOne('/api/auth/me').flush('boom', { status: 500, statusText: 'Server Error' });
      await initPromise;

      expect(fresh.currentUser).toBeNull();
      expect(fresh.isAdmin()).toBe(false);
      freshHttpMock.verify();
    });
  });

  it('login stores the token, loads the current user, and flips isAuthenticated', async () => {
    const resetSpy = vi.spyOn(storeService, 'reset');
    const loginPromise = service.login('alice', 'secret');

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'alice', password: 'secret' });
    req.flush({ token: 'new-token' });

    await Promise.resolve();
    httpMock.expectOne('/api/auth/me').flush(adminSummary);

    await loginPromise;

    expect(service.token).toBe('new-token');
    expect(service.isAuthenticated()).toBe(true);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('new-token');
    expect(resetSpy).toHaveBeenCalled();
    expect(service.currentUser).toEqual(adminSummary);
    expect(service.isAdmin()).toBe(true);
  });

  it('login rejects and leaves state untouched when the server rejects credentials', async () => {
    const loginPromise = service.login('alice', 'wrong');

    const req = httpMock.expectOne('/api/auth/login');
    req.flush({ message: 'bad credentials' }, { status: 401, statusText: 'Unauthorized' });

    await expect(loginPromise).rejects.toBeTruthy();
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(service.currentUser).toBeNull();
  });

  it('logout clears the token, current user, and resets store state', async () => {
    const loginPromise = service.login('alice', 'secret');
    httpMock.expectOne('/api/auth/login').flush({ token: 'tok' });
    await Promise.resolve();
    httpMock.expectOne('/api/auth/me').flush(plainUserSummary);
    await loginPromise;

    const resetSpy = vi.spyOn(storeService, 'reset');
    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token).toBeNull();
    expect(service.currentUser).toBeNull();
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(resetSpy).toHaveBeenCalled();
  });
});
