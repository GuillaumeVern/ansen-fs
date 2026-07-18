import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
import { RoleSummary } from '../../../models/rbac';

const PROTECTED_ROLE_NAMES = new Set(['ADMIN', 'USER_ROLE']);

@Component({
  selector: 'app-roles-panel',
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
  templateUrl: './roles-panel.html',
})
export class RolesPanel implements OnInit {
  private adminService = inject(AdminService);
  private fb = inject(FormBuilder);

  protected roles = this.adminService.roles;
  protected permissions = this.adminService.permissions;

  protected showModal = signal(false);
  protected editingRoleId = signal<number | null>(null);
  protected form = this.fb.nonNullable.group({
    name: ['', Validators.required],
  });
  protected selectedPermissionIds = signal<number[]>([]);
  protected editingIsProtected = computed(() => {
    const id = this.editingRoleId();
    if (id === null) return false;
    const role = this.roles().find((r) => r.id === id);
    return role ? this.isProtected(role) : false;
  });

  async ngOnInit() {
    if (this.permissions().length === 0) {
      await this.adminService.loadPermissions();
    }
  }

  isProtected(role: RoleSummary): boolean {
    return PROTECTED_ROLE_NAMES.has(role.name.toUpperCase());
  }

  openCreate(): void {
    this.editingRoleId.set(null);
    this.form.reset();
    this.selectedPermissionIds.set([]);
    this.showModal.set(true);
  }

  openEdit(role: RoleSummary): void {
    this.editingRoleId.set(role.id);
    this.form.reset({ name: role.name });
    this.selectedPermissionIds.set(role.permissions.map((p) => p.id));
    this.showModal.set(true);
  }

  cancel(): void {
    this.showModal.set(false);
  }

  async save(): Promise<void> {
    if (this.form.invalid) return;
    const { name } = this.form.getRawValue();
    const permissionIds = this.selectedPermissionIds();

    try {
      const id = this.editingRoleId();
      if (id === null) {
        await this.adminService.createRole({ name, permissionIds });
      } else {
        await this.adminService.updateRole(id, { name, permissionIds });
      }
      this.showModal.set(false);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to save role.');
    }
  }

  async deleteRole(role: RoleSummary): Promise<void> {
    try {
      await this.adminService.deleteRole(role.id);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to delete role.');
    }
  }
}
