import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { RolesPanel } from './roles-panel';
import { AdminService } from '../../../services/admin';
import { PermissionSummary, RoleSummary } from '../../../models/rbac';
import { icons } from '../../../icons-provider';

if (!(globalThis as any).ResizeObserver) {
  (globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

const readPermission: PermissionSummary = { id: 1, name: 'READ' };
const adminRole: RoleSummary = { id: 1, name: 'ADMIN', permissions: [] };
const userRole: RoleSummary = { id: 2, name: 'USER_ROLE', permissions: [] };
const editorRole: RoleSummary = { id: 3, name: 'EDITOR', permissions: [readPermission] };

describe('RolesPanel', () => {
  let component: RolesPanel;
  let fixture: ComponentFixture<RolesPanel>;
  let adminServiceStub: {
    roles: () => RoleSummary[];
    permissions: () => PermissionSummary[];
    loadPermissions: ReturnType<typeof vi.fn>;
    createRole: ReturnType<typeof vi.fn>;
    updateRole: ReturnType<typeof vi.fn>;
    deleteRole: ReturnType<typeof vi.fn>;
  };

  let permissionsValue: PermissionSummary[];

  beforeEach(async () => {
    permissionsValue = [readPermission];
    adminServiceStub = {
      roles: () => [adminRole, userRole, editorRole],
      permissions: () => permissionsValue,
      loadPermissions: vi.fn().mockResolvedValue(undefined),
      createRole: vi.fn().mockResolvedValue(undefined),
      updateRole: vi.fn().mockResolvedValue(undefined),
      deleteRole: vi.fn().mockResolvedValue(undefined),
    };

    await TestBed.configureTestingModule({
      imports: [RolesPanel],
      providers: [provideNzIcons(icons), { provide: AdminService, useValue: adminServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(RolesPanel);
    component = fixture.componentInstance;
  });

  it('should create and render the role list', async () => {
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('EDITOR');
  });

  it('ngOnInit loads permissions when the list is empty', async () => {
    permissionsValue = [];
    await component.ngOnInit();
    expect(adminServiceStub.loadPermissions).toHaveBeenCalled();
  });

  it('ngOnInit skips loading permissions when already present', async () => {
    await component.ngOnInit();
    expect(adminServiceStub.loadPermissions).not.toHaveBeenCalled();
  });

  it('isProtected recognizes built-in role names case-insensitively', () => {
    expect(component.isProtected(adminRole)).toBe(true);
    expect(component.isProtected(userRole)).toBe(true);
    expect(component.isProtected(editorRole)).toBe(false);
    expect(component.isProtected({ ...editorRole, name: 'admin' })).toBe(true);
  });

  describe('create/edit flow', () => {
    it('openCreate resets the form for a new role', () => {
      component.openEdit(editorRole);
      component.openCreate();

      expect(component['editingRoleId']()).toBeNull();
      expect(component['selectedPermissionIds']()).toEqual([]);
      expect(component['showModal']()).toBe(true);
    });

    it('openEdit preloads the role name and permissions', () => {
      component.openEdit(editorRole);

      expect(component['editingRoleId']()).toBe(3);
      expect(component['form'].getRawValue().name).toBe('EDITOR');
      expect(component['selectedPermissionIds']()).toEqual([1]);
    });

    it('cancel closes the modal', () => {
      component.openCreate();
      component.cancel();
      expect(component['showModal']()).toBe(false);
    });

    it('editingIsProtected reflects the role currently being edited', () => {
      component.openEdit(adminRole);
      expect(component['editingIsProtected']()).toBe(true);

      component.openEdit(editorRole);
      expect(component['editingIsProtected']()).toBe(false);
    });

    it('does nothing when the form is invalid', async () => {
      component.openCreate();
      component['form'].setValue({ name: '' });

      await component.save();

      expect(adminServiceStub.createRole).not.toHaveBeenCalled();
      expect(adminServiceStub.updateRole).not.toHaveBeenCalled();
    });

    it('creates a new role when not editing an existing one', async () => {
      component.openCreate();
      component['form'].setValue({ name: 'VIEWER' });
      component['selectedPermissionIds'].set([1]);

      await component.save();

      expect(adminServiceStub.createRole).toHaveBeenCalledWith({ name: 'VIEWER', permissionIds: [1] });
      expect(component['showModal']()).toBe(false);
    });

    it('updates the role being edited', async () => {
      component.openEdit(editorRole);
      component['form'].setValue({ name: 'SENIOR_EDITOR' });

      await component.save();

      expect(adminServiceStub.updateRole).toHaveBeenCalledWith(3, { name: 'SENIOR_EDITOR', permissionIds: [1] });
    });

    it('alerts on failure and keeps the modal open', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.createRole.mockRejectedValue({ error: 'boom' });
      component.openCreate();
      component['form'].setValue({ name: 'VIEWER' });

      await component.save();

      expect(alertSpy).toHaveBeenCalledWith('boom');
      expect(component['showModal']()).toBe(true);
      alertSpy.mockRestore();
    });
  });

  describe('deleteRole', () => {
    it('delegates to the admin service', async () => {
      await component.deleteRole(editorRole);
      expect(adminServiceStub.deleteRole).toHaveBeenCalledWith(3);
    });

    it('alerts on failure', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.deleteRole.mockRejectedValue({ error: 'The built-in ADMIN role cannot be deleted.' });

      await component.deleteRole(adminRole);

      expect(alertSpy).toHaveBeenCalledWith('The built-in ADMIN role cannot be deleted.');
      alertSpy.mockRestore();
    });
  });
});
