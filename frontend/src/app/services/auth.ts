import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { StoreService } from './store';
import { UserSummary } from '../models/rbac';

interface AuthResponse {
  token: string;
}

const TOKEN_STORAGE_KEY = 'anzenfs.token';
const ADMIN_ROLE_NAME = 'ADMIN';

/**
 * Plain fields, no signals: the user is loaded exactly once, at app startup, by `init()`
 * (see the app initializer in app.config.ts) before the router ever runs a guard. That's the
 * only place the current user is fetched, so there's no concurrent/duplicate request and no
 * ordering race to reason about - the previous signal-based version had two independent call
 * sites (a constructor fire-and-forget call and a guard-triggered call) that could race and
 * silently revoke admin access after the page had already rendered.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private storeService = inject(StoreService);

  token: string | null = localStorage.getItem(TOKEN_STORAGE_KEY);
  currentUser: UserSummary | null = null;

  isAuthenticated(): boolean {
    return !!this.token;
  }

  isAdmin(): boolean {
    return this.currentUser?.roles.some((role) => role.name === ADMIN_ROLE_NAME) ?? false;
  }

  /** Called once from the app initializer, before routing starts. */
  async init(): Promise<void> {
    if (this.token) {
      await this.loadCurrentUser();
    }
  }

  async login(username: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AuthResponse>('/api/auth/login', { username, password }),
    );

    this.storeService.reset();
    this.token = response.token;
    localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
    await this.loadCurrentUser();
  }

  logout(): void {
    this.storeService.reset();
    this.token = null;
    this.currentUser = null;
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  }

  private async loadCurrentUser(): Promise<void> {
    try {
      this.currentUser = await firstValueFrom(this.http.get<UserSummary>('/api/auth/me'));
    } catch {
      this.currentUser = null;
    }
  }
}
