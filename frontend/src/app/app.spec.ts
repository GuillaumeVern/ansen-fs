import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { App } from './app';
import { AuthService } from './services/auth';
import { icons } from './icons-provider';

describe('App', () => {
  let authServiceStub: { isAuthenticated: () => boolean; isAdmin: () => boolean; logout: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceStub = { isAuthenticated: () => false, isAdmin: () => false, logout: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideNzIcons(icons), { provide: AuthService, useValue: authServiceStub }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('hides the logout button when unauthenticated', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(compiled.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons).not.toContain('Log out');
  });

  it('shows the logout button when authenticated', async () => {
    authServiceStub.isAuthenticated = () => true;
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(compiled.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(buttons.some((b) => b?.includes('Log out'))).toBe(true);
  });

  it('hides the Admin nav link for non-admin users', async () => {
    authServiceStub.isAuthenticated = () => true;
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Admin');
  });

  it('shows the Admin nav link for admin users', async () => {
    authServiceStub.isAuthenticated = () => true;
    authServiceStub.isAdmin = () => true;
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Admin');
  });

  it('logout() calls the auth service and navigates to /login', () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.logout();

    expect(authServiceStub.logout).toHaveBeenCalled();
    expect(navSpy).toHaveBeenCalledWith('/login');
  });
});
