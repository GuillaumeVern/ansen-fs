import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { Login } from './login';
import { AuthService } from '../../services/auth';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authServiceStub: { login: ReturnType<typeof vi.fn> };
  let routerStub: { navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceStub = { login: vi.fn() };
    routerStub = { navigateByUrl: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not call the auth service when the form is invalid', async () => {
    await component.onSubmit();
    expect(authServiceStub.login).not.toHaveBeenCalled();
  });

  it('logs in and navigates to /files on success', async () => {
    authServiceStub.login.mockResolvedValue(undefined);
    (component as any).form.setValue({ username: 'alice', password: 'secret' });

    await component.onSubmit();

    expect(authServiceStub.login).toHaveBeenCalledWith('alice', 'secret');
    expect(routerStub.navigateByUrl).toHaveBeenCalledWith('/files');
    expect((component as any).errorMessage()).toBeNull();
    expect((component as any).isSubmitting()).toBe(false);
  });

  it('shows an error message and does not navigate when login fails', async () => {
    authServiceStub.login.mockRejectedValue(new Error('bad credentials'));
    (component as any).form.setValue({ username: 'alice', password: 'wrong' });

    await component.onSubmit();

    expect((component as any).errorMessage()).toBe('Invalid username or password.');
    expect(routerStub.navigateByUrl).not.toHaveBeenCalled();
    expect((component as any).isSubmitting()).toBe(false);
  });

  it('ignores a second submit while one is already in flight', async () => {
    let resolveLogin: () => void = () => {};
    authServiceStub.login.mockReturnValue(new Promise<void>((resolve) => (resolveLogin = resolve)));
    (component as any).form.setValue({ username: 'alice', password: 'secret' });

    const first = component.onSubmit();
    const second = component.onSubmit();

    resolveLogin();
    await Promise.all([first, second]);

    expect(authServiceStub.login).toHaveBeenCalledTimes(1);
  });
});
