import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { PermissionsPanel } from './permissions-panel';
import { AdminService } from '../../../services/admin';
import { PermissionSummary } from '../../../models/rbac';
import { icons } from '../../../icons-provider';

const readPermission: PermissionSummary = { id: 1, name: 'READ' };

describe('PermissionsPanel', () => {
  let component: PermissionsPanel;
  let fixture: ComponentFixture<PermissionsPanel>;
  let adminServiceStub: {
    permissions: () => PermissionSummary[];
    createPermission: ReturnType<typeof vi.fn>;
    deletePermission: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    adminServiceStub = {
      permissions: () => [readPermission],
      createPermission: vi.fn().mockResolvedValue(undefined),
      deletePermission: vi.fn().mockResolvedValue(undefined),
    };

    await TestBed.configureTestingModule({
      imports: [PermissionsPanel],
      providers: [provideNzIcons(icons), { provide: AdminService, useValue: adminServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(PermissionsPanel);
    component = fixture.componentInstance;
  });

  it('should create and render the permission list', async () => {
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('READ');
  });

  describe('add', () => {
    it('does nothing when the form is invalid', async () => {
      component['form'].setValue({ name: '' });
      await component.add();
      expect(adminServiceStub.createPermission).not.toHaveBeenCalled();
    });

    it('creates the permission and resets the form on success', async () => {
      component['form'].setValue({ name: 'WRITE' });

      await component.add();

      expect(adminServiceStub.createPermission).toHaveBeenCalledWith('WRITE');
      expect(component['form'].getRawValue().name).toBe('');
    });

    it('alerts on failure and keeps the entered value', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.createPermission.mockRejectedValue({ error: 'boom' });
      component['form'].setValue({ name: 'WRITE' });

      await component.add();

      expect(alertSpy).toHaveBeenCalledWith('boom');
      expect(component['form'].getRawValue().name).toBe('WRITE');
      alertSpy.mockRestore();
    });
  });

  describe('remove', () => {
    it('delegates to the admin service', async () => {
      await component.remove(readPermission);
      expect(adminServiceStub.deletePermission).toHaveBeenCalledWith(1);
    });

    it('alerts on failure', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      adminServiceStub.deletePermission.mockRejectedValue({ error: 'boom' });

      await component.remove(readPermission);

      expect(alertSpy).toHaveBeenCalledWith('boom');
      alertSpy.mockRestore();
    });
  });
});
