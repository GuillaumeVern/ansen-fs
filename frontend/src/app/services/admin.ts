import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PermissionSummary, RoleSummary, UserSummary } from '../models/rbac';

export interface RoleWriteRequest {
  name: string;
  permissionIds: number[];
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);

  public users = signal<UserSummary[]>([]);
  public roles = signal<RoleSummary[]>([]);
  public permissions = signal<PermissionSummary[]>([]);

  async loadUsers(): Promise<void> {
    const users = await firstValueFrom(this.http.get<UserSummary[]>('/api/admin/users'));
    this.users.set(users);
  }

  async loadRoles(): Promise<void> {
    const roles = await firstValueFrom(this.http.get<RoleSummary[]>('/api/admin/roles'));
    this.roles.set(roles);
  }

  async loadPermissions(): Promise<void> {
    const permissions = await firstValueFrom(this.http.get<PermissionSummary[]>('/api/admin/permissions'));
    this.permissions.set(permissions);
  }

  async createUser(username: string, password: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/users/create', { username, password }));
    await this.loadUsers();
  }

  async updateUserRoles(userId: number, roleIds: number[]): Promise<void> {
    const updated = await firstValueFrom(
      this.http.put<UserSummary>(`/api/admin/users/${userId}/roles`, { roleIds }),
    );
    this.users.update((current) => current.map((u) => (u.id === userId ? updated : u)));
  }

  async updateUserPassword(userId: number, newPassword: string): Promise<void> {
    await firstValueFrom(this.http.put(`/api/admin/users/${userId}/password`, { newPassword }));
  }

  async deleteUser(userId: number): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/admin/users/${userId}`, { responseType: 'text' }));
    this.users.update((current) => current.filter((u) => u.id !== userId));
  }

  async createRole(request: RoleWriteRequest): Promise<void> {
    const created = await firstValueFrom(this.http.post<RoleSummary>('/api/admin/roles', request));
    this.roles.update((current) => [...current, created]);
  }

  async updateRole(roleId: number, request: RoleWriteRequest): Promise<void> {
    const updated = await firstValueFrom(this.http.put<RoleSummary>(`/api/admin/roles/${roleId}`, request));
    this.roles.update((current) => current.map((r) => (r.id === roleId ? updated : r)));
  }

  async deleteRole(roleId: number): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/admin/roles/${roleId}`, { responseType: 'text' }));
    this.roles.update((current) => current.filter((r) => r.id !== roleId));
  }

  async createPermission(name: string): Promise<void> {
    const created = await firstValueFrom(this.http.post<PermissionSummary>('/api/admin/permissions', { name }));
    this.permissions.update((current) => [...current, created]);
  }

  async deletePermission(permissionId: number): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/admin/permissions/${permissionId}`, { responseType: 'text' }));
    this.permissions.update((current) => current.filter((p) => p.id !== permissionId));
  }
}
