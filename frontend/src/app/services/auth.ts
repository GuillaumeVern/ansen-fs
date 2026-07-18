import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { StoreService } from './store';

interface AuthResponse {
  token: string;
}

const TOKEN_STORAGE_KEY = 'anzenfs.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private storeService = inject(StoreService);

  private tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY));
  public token = this.tokenSignal.asReadonly();
  public isAuthenticated = computed(() => !!this.tokenSignal());

  async login(username: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AuthResponse>('/api/auth/login', { username, password }),
    );

    this.storeService.reset();
    this.tokenSignal.set(response.token);
    localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
  }

  logout(): void {
    this.storeService.reset();
    this.tokenSignal.set(null);
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  }
}
