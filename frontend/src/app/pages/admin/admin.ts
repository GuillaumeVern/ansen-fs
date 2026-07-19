import { Component, OnInit, inject } from '@angular/core';
import { NzTabsModule } from 'ng-zorro-antd/tabs';

import { AdminService } from '../../services/admin';
import { UsersPanel } from './users-panel/users-panel';
import { RolesPanel } from './roles-panel/roles-panel';
import { PermissionsPanel } from './permissions-panel/permissions-panel';

@Component({
  selector: 'app-admin',
  imports: [NzTabsModule, UsersPanel, RolesPanel, PermissionsPanel],
  templateUrl: './admin.html',
  styleUrl: './admin.scss',
})
export class Admin implements OnInit {
  private adminService = inject(AdminService);

  async ngOnInit() {
    await Promise.all([
      this.adminService.loadUsers(),
      this.adminService.loadRoles(),
      this.adminService.loadPermissions(),
    ]);
  }
}
