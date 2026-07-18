import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { authInterceptor } from './auth-interceptor';

const TOKEN_STORAGE_KEY = 'anzenfs.token';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;

  function setup(token: string | null) {
    localStorage.clear();
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    }
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('attaches the bearer token to non-auth API requests', () => {
    setup('my-token');

    httpClient.get('/api/files').subscribe();

    const req = httpMock.expectOne('/api/files');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-token');
    req.flush([]);
  });

  it('does not attach a header when there is no token', () => {
    setup(null);

    httpClient.get('/api/files').subscribe();

    const req = httpMock.expectOne('/api/files');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('does not attach a header to auth endpoints even when a token is present', () => {
    setup('my-token');

    httpClient.post('/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
