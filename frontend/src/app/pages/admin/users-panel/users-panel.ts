import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { AdminService } from '../../../services/admin';
import { AuthService } from '../../../services/auth';
import { UserSummary } from '../../../models/rbac';

@Component({
  selector: 'app-users-panel',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    NzButtonModule,
    NzFormModule,
    NzIconModule,
    NzInputModule,
    NzModalModule,
    NzPopconfirmModule,
    NzSelectModule,
    NzTableModule,
    NzTagModule,
  ],
  templateUrl: './users-panel.html',
})
export class UsersPanel implements OnInit {
  private adminService = inject(AdminService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  protected users = this.adminService.users;
  protected roles = this.adminService.roles;

  protected showCreateModal = signal(false);
  protected createForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected editingUser = signal<UserSummary | null>(null);
  protected selectedRoleIds = signal<number[]>([]);

  protected resettingPasswordUser = signal<UserSummary | null>(null);
  protected passwordForm = this.fb.nonNullable.group({
    newPassword: ['', Validators.required],
  });

  async ngOnInit() {
    if (this.roles().length === 0) {
      await this.adminService.loadRoles();
    }
  }

  protected currentUserId(): number | undefined {
    return this.authService.currentUser?.id;
  }

  openCreateModal(): void {
    this.createForm.reset();
    this.showCreateModal.set(true);
  }

  cancelCreate(): void {
    this.showCreateModal.set(false);
  }

  async submitCreate(): Promise<void> {
    if (this.createForm.invalid) return;
    const { username, password } = this.createForm.getRawValue();

    try {
      await this.adminService.createUser(username, password);
      this.showCreateModal.set(false);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to create user.');
    }
  }

  openEditRoles(user: UserSummary): void {
    this.editingUser.set(user);
    this.selectedRoleIds.set(user.roles.map((r) => r.id));
  }

  cancelEditRoles(): void {
    this.editingUser.set(null);
  }

  async saveRoles(): Promise<void> {
    const user = this.editingUser();
    if (!user) return;

    try {
      await this.adminService.updateUserRoles(user.id, this.selectedRoleIds());
      this.editingUser.set(null);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to update roles.');
    }
  }

  async deleteUser(user: UserSummary): Promise<void> {
    try {
      await this.adminService.deleteUser(user.id);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to delete user.');
    }
  }

  openResetPassword(user: UserSummary): void {
    this.resettingPasswordUser.set(user);
    this.passwordForm.reset();
  }

  cancelResetPassword(): void {
    this.resettingPasswordUser.set(null);
  }

  async submitResetPassword(): Promise<void> {
    const user = this.resettingPasswordUser();
    if (!user || this.passwordForm.invalid) return;

    try {
      await this.adminService.updateUserPassword(user.id, this.passwordForm.getRawValue().newPassword);
      this.resettingPasswordUser.set(null);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to reset password.');
    }
  }
}
