import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { AuthService } from './auth';
import { StoreService } from './store';

const TOKEN_STORAGE_KEY = 'anzenfs.token';

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
    expect(service.token()).toBeNull();
  });

  it('picks up a pre-existing token from localStorage on construction', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'preexisting-token');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    const fresh = TestBed.inject(AuthService);
    expect(fresh.token()).toBe('preexisting-token');
  });

  it('login stores the token and flips isAuthenticated', async () => {
    const resetSpy = vi.spyOn(storeService, 'reset');
    const loginPromise = service.login('alice', 'secret');

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'alice', password: 'secret' });
    req.flush({ token: 'new-token' });

    await loginPromise;

    expect(service.token()).toBe('new-token');
    expect(service.isAuthenticated()).toBe(true);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('new-token');
    expect(resetSpy).toHaveBeenCalled();
  });

  it('login rejects and leaves state untouched when the server rejects credentials', async () => {
    const loginPromise = service.login('alice', 'wrong');

    const req = httpMock.expectOne('/api/auth/login');
    req.flush({ message: 'bad credentials' }, { status: 401, statusText: 'Unauthorized' });

    await expect(loginPromise).rejects.toBeTruthy();
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
  });

  it('logout clears the token and resets store state', async () => {
    const loginPromise = service.login('alice', 'secret');
    httpMock.expectOne('/api/auth/login').flush({ token: 'tok' });
    await loginPromise;

    const resetSpy = vi.spyOn(storeService, 'reset');
    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(resetSpy).toHaveBeenCalled();
  });
});
