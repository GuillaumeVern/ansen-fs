import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { StoreService } from './store';
import { UserSummary } from '../models/rbac';

interface AuthResponse {
  token: string;
}

const TOKEN_STORAGE_KEY = 'anzenfs.token';
const ADMIN_ROLE_NAME = 'ADMIN';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private storeService = inject(StoreService);

  private tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY));
  public token = this.tokenSignal.asReadonly();
  public isAuthenticated = computed(() => !!this.tokenSignal());

  private currentUserSignal = signal<UserSummary | null>(null);
  public currentUser = this.currentUserSignal.asReadonly();
  public isAdmin = computed(() =>
    this.currentUserSignal()?.roles.some((role) => role.name === ADMIN_ROLE_NAME) ?? false,
  );

  constructor() {
    if (this.tokenSignal()) {
      this.loadCurrentUser();
    }
  }

  async login(username: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AuthResponse>('/api/auth/login', { username, password }),
    );

    this.storeService.reset();
    this.tokenSignal.set(response.token);
    localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
    await this.loadCurrentUser();
  }

  logout(): void {
    this.storeService.reset();
    this.tokenSignal.set(null);
    this.currentUserSignal.set(null);
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  }

  async loadCurrentUser(): Promise<void> {
    try {
      const user = await firstValueFrom(this.http.get<UserSummary>('/api/auth/me'));
      this.currentUserSignal.set(user);
    } catch {
      this.currentUserSignal.set(null);
    }
  }
}
