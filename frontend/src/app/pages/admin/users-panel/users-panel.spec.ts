import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { UsersPanel } from './users-panel';
import { AdminService } from '../../../services/admin';
import { AuthService } from '../../../services/auth';
import { RoleSummary, UserSummary } from '../../../models/rbac';
import { icons } from '../../../icons-provider';

if (!(globalThis as any).ResizeObserver) {
  (globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

const adminRole: RoleSummary = { id: 1, name: 'ADMIN', permissions: [] };
const userRole: RoleSummary = { id: 2, name: 'USER_ROLE', permissions: [] };
const bob: UserSummary = { id: 2, username: 'bob', roles: [userRole] };

describe('UsersPanel', () => {
  let component: UsersPanel;
  let fixture: ComponentFixture<UsersPanel>;
  let adminServiceStub: {
    users: () => UserSummary[];
    roles: () => RoleSummary[];
    loadRoles: ReturnType<typeof vi.fn>;
    createUser: ReturnType<typeof vi.fn>;
    updateUserRoles: ReturnType<typeof vi.fn>;
    updateUserPassword: ReturnType<typeof vi.fn>;
    deleteUser: ReturnType<typeof vi.fn>;
  };

  let rolesValue: RoleSummary[];

  beforeEach(async () => {
    rolesValue = [adminRole, userRole];
    adminServiceStub = {
      users: () => [bob],
      roles: () => rolesValue,
      loadRoles: vi.fn().mockResolvedValue(undefined),
      createUser: vi.fn().mockResolvedValue(undefined),
      updateUserRoles: vi.fn().mockResolvedValue(undefined),
      updateUserPassword: vi.fn().mockResolvedValue(undefined),
      deleteUser: vi.fn().mockResolvedValue(undefined),
    };
    const authServiceStub = { currentUser: { id: 1, username: 'admin', roles: [adminRole] } };

    await TestBed.configureTestingModule({
      imports: [UsersPanel],
      providers: [
        provideNzIcons(icons),
        { provide: AdminService, useValue: adminServiceStub },
        { provide: AuthService, useValue: authServiceStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UsersPanel);
    component = fixture.componentInstance;
  });

  it('should create and render the user list', async () => {
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('bob');
  });

  it('ngOnInit skips loading roles when they are already present', async () => {
    await component.ngOnInit();
    expect(adminServiceStub.loadRoles).not.toHaveBeenCalled();
  });

  it('ngOnInit loads roles when the list is empty', async () => {
    rolesValue = [];
    await component.ngOnInit();
    expect(adminServiceStub.loadRoles).toHaveBeenCalled();
  });

  describe('create user flow', () => {
    it('opens and cancels the create modal', () => {
      component.openCreateModal();
      expect(component['showCreateModal']()).toBe(true);
      component.cancelCreate();
      expect(component['showCreateModal']()).toBe(false);
    });

    it('does nothing when the form is invalid', async () => {
      component.openCreateModal();
      await component.submitCreate();
      expect(adminServiceStub.createUser).not.toHaveBeenCalled();
    });

    it('creates the user and closes the modal on success', async () => {
      component.openCreateModal();
      component['createForm'].setValue({ username: 'carol', password: 'secret' });

      await component.submitCreate();

      expect(adminServiceStub.createUser).toHaveBeenCalledWith('carol', 'secret');
      expect(component['showCreateModal']()).toBe(false);
    });

    it('shows an alert and keeps the modal open on failure', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.createUser.mockRejectedValue({ error: 'username taken' });
      component.openCreateModal();
      component['createForm'].setValue({ username: 'carol', password: 'secret' });

      await component.submitCreate();

      expect(alertSpy).toHaveBeenCalledWith('username taken');
      expect(component['showCreateModal']()).toBe(true);
      alertSpy.mockRestore();
    });
  });

  describe('edit roles flow', () => {
    it('opens with the user preselected roles and cancels', () => {
      component.openEditRoles(bob);
      expect(component['editingUser']()).toEqual(bob);
      expect(component['selectedRoleIds']()).toEqual([2]);

      component.cancelEditRoles();
      expect(component['editingUser']()).toBeNull();
    });

    it('saves the selected roles', async () => {
      component.openEditRoles(bob);
      component['selectedRoleIds'].set([1, 2]);

      await component.saveRoles();

      expect(adminServiceStub.updateUserRoles).toHaveBeenCalledWith(2, [1, 2]);
      expect(component['editingUser']()).toBeNull();
    });

    it('does nothing when there is no user being edited', async () => {
      await component.saveRoles();
      expect(adminServiceStub.updateUserRoles).not.toHaveBeenCalled();
    });

    it('alerts on failure and keeps the modal open', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.updateUserRoles.mockRejectedValue({ error: 'You cannot remove your own ADMIN role.' });
      component.openEditRoles(bob);

      await component.saveRoles();

      expect(alertSpy).toHaveBeenCalledWith('You cannot remove your own ADMIN role.');
      expect(component['editingUser']()).toEqual(bob);
      alertSpy.mockRestore();
    });
  });

  describe('reset password flow', () => {
    it('opens and cancels', () => {
      component.openResetPassword(bob);
      expect(component['resettingPasswordUser']()).toEqual(bob);
      component.cancelResetPassword();
      expect(component['resettingPasswordUser']()).toBeNull();
    });

    it('does nothing when the form is invalid', async () => {
      component.openResetPassword(bob);
      await component.submitResetPassword();
      expect(adminServiceStub.updateUserPassword).not.toHaveBeenCalled();
    });

    it('submits the new password and closes the modal', async () => {
      component.openResetPassword(bob);
      component['passwordForm'].setValue({ newPassword: 'newpass123' });

      await component.submitResetPassword();

      expect(adminServiceStub.updateUserPassword).toHaveBeenCalledWith(2, 'newpass123');
      expect(component['resettingPasswordUser']()).toBeNull();
    });
  });

  describe('deleteUser', () => {
    it('delegates to the admin service', async () => {
      await component.deleteUser(bob);
      expect(adminServiceStub.deleteUser).toHaveBeenCalledWith(2);
    });

    it('alerts on failure', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.deleteUser.mockRejectedValue({ error: 'nope' });

      await component.deleteUser(bob);

      expect(alertSpy).toHaveBeenCalledWith('nope');
      alertSpy.mockRestore();
    });
  });
});
