import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzTableModule } from 'ng-zorro-antd/table';

import { AdminService } from '../../../services/admin';
import { PermissionSummary } from '../../../models/rbac';

@Component({
  selector: 'app-permissions-panel',
  imports: [ReactiveFormsModule, NzButtonModule, NzIconModule, NzInputModule, NzPopconfirmModule, NzTableModule],
  templateUrl: './permissions-panel.html',
})
export class PermissionsPanel {
  private adminService = inject(AdminService);
  private fb = inject(FormBuilder);

  protected permissions = this.adminService.permissions;
  protected form = this.fb.nonNullable.group({
    name: ['', Validators.required],
  });

  async add(): Promise<void> {
    if (this.form.invalid) return;

    try {
      await this.adminService.createPermission(this.form.getRawValue().name);
      this.form.reset();
    } catch (err: any) {
      alert(err?.error ?? 'Failed to create permission.');
    }
  }

  async remove(permission: PermissionSummary): Promise<void> {
    try {
      await this.adminService.deletePermission(permission.id);
    } catch (err: any) {
      alert(err?.error ?? 'Failed to delete permission.');
    }
  }
}
