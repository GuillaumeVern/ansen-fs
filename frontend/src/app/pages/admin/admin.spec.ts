import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { Admin } from './admin';
import { AdminService } from '../../services/admin';
import { AuthService } from '../../services/auth';
import { icons } from '../../icons-provider';

if (!(globalThis as any).ResizeObserver) {
  (globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

describe('Admin', () => {
  let component: Admin;
  let fixture: ComponentFixture<Admin>;
  let adminServiceStub: {
    users: () => any[];
    roles: () => any[];
    permissions: () => any[];
    loadUsers: ReturnType<typeof vi.fn>;
    loadRoles: ReturnType<typeof vi.fn>;
    loadPermissions: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    adminServiceStub = {
      users: () => [],
      roles: () => [],
      permissions: () => [],
      loadUsers: vi.fn().mockResolvedValue(undefined),
      loadRoles: vi.fn().mockResolvedValue(undefined),
      loadPermissions: vi.fn().mockResolvedValue(undefined),
    };
    const authServiceStub = { currentUser: () => null, isAdmin: () => true };

    await TestBed.configureTestingModule({
      imports: [Admin],
      providers: [
        provideNzIcons(icons),
        { provide: AdminService, useValue: adminServiceStub },
        { provide: AuthService, useValue: authServiceStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Admin);
    component = fixture.componentInstance;
  });

  it('should create', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('ngOnInit loads users, roles, and permissions', async () => {
    await component.ngOnInit();

    expect(adminServiceStub.loadUsers).toHaveBeenCalled();
    expect(adminServiceStub.loadRoles).toHaveBeenCalled();
    expect(adminServiceStub.loadPermissions).toHaveBeenCalled();
  });
});
