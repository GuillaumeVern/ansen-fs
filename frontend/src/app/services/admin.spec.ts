import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AdminService } from './admin';
import { PermissionSummary, RoleSummary, UserSummary } from '../models/rbac';

const user: UserSummary = { id: 1, username: 'alice', roles: [] };
const role: RoleSummary = { id: 1, name: 'EDITOR', permissions: [] };
const permission: PermissionSummary = { id: 1, name: 'READ' };

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loadUsers populates the users signal', async () => {
    const promise = service.loadUsers();
    httpMock.expectOne('/api/admin/users').flush([user]);
    await promise;

    expect(service.users()).toEqual([user]);
  });

  it('loadRoles populates the roles signal', async () => {
    const promise = service.loadRoles();
    httpMock.expectOne('/api/admin/roles').flush([role]);
    await promise;

    expect(service.roles()).toEqual([role]);
  });

  it('loadPermissions populates the permissions signal', async () => {
    const promise = service.loadPermissions();
    httpMock.expectOne('/api/admin/permissions').flush([permission]);
    await promise;

    expect(service.permissions()).toEqual([permission]);
  });

  it('createUser posts to the public signup endpoint then refreshes the user list', async () => {
    const promise = service.createUser('bob', 'secret');

    const createReq = httpMock.expectOne('/api/users/create');
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body).toEqual({ username: 'bob', password: 'secret' });
    createReq.flush({});

    await Promise.resolve();
    httpMock.expectOne('/api/admin/users').flush([user]);
    await promise;

    expect(service.users()).toEqual([user]);
  });

  it('updateUserRoles PUTs the role ids and replaces the user in state', async () => {
    service.users.set([user]);
    const updatedUser: UserSummary = { ...user, roles: [role] };

    const promise = service.updateUserRoles(1, [1]);
    const req = httpMock.expectOne('/api/admin/users/1/roles');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roleIds: [1] });
    req.flush(updatedUser);
    await promise;

    expect(service.users()).toEqual([updatedUser]);
  });

  it('updateUserPassword PUTs the new password', async () => {
    const promise = service.updateUserPassword(1, 'newpass');
    const req = httpMock.expectOne('/api/admin/users/1/password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ newPassword: 'newpass' });
    req.flush({});
    await promise;
  });

  it('deleteUser removes the user from state on success', async () => {
    service.users.set([user]);

    const promise = service.deleteUser(1);
    const req = httpMock.expectOne('/api/admin/users/1');
    expect(req.request.method).toBe('DELETE');
    req.flush('ok');
    await promise;

    expect(service.users()).toEqual([]);
  });

  it('createRole posts the request and appends the created role', async () => {
    const promise = service.createRole({ name: 'EDITOR', permissionIds: [1] });
    const req = httpMock.expectOne('/api/admin/roles');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'EDITOR', permissionIds: [1] });
    req.flush(role);
    await promise;

    expect(service.roles()).toEqual([role]);
  });

  it('updateRole PUTs the request and replaces the role in state', async () => {
    service.roles.set([role]);
    const renamed: RoleSummary = { ...role, name: 'SENIOR_EDITOR' };

    const promise = service.updateRole(1, { name: 'SENIOR_EDITOR', permissionIds: [] });
    httpMock.expectOne('/api/admin/roles/1').flush(renamed);
    await promise;

    expect(service.roles()).toEqual([renamed]);
  });

  it('deleteRole removes the role from state on success', async () => {
    service.roles.set([role]);

    const promise = service.deleteRole(1);
    httpMock.expectOne('/api/admin/roles/1').flush('ok');
    await promise;

    expect(service.roles()).toEqual([]);
  });

  it('createPermission posts the name and appends the created permission', async () => {
    const promise = service.createPermission('READ');
    const req = httpMock.expectOne('/api/admin/permissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'READ' });
    req.flush(permission);
    await promise;

    expect(service.permissions()).toEqual([permission]);
  });

  it('deletePermission removes the permission from state on success', async () => {
    service.permissions.set([permission]);

    const promise = service.deletePermission(1);
    httpMock.expectOne('/api/admin/permissions/1').flush('ok');
    await promise;

    expect(service.permissions()).toEqual([]);
  });

  it('propagates the error when a write fails', async () => {
    const promise = service.deleteRole(1);
    httpMock.expectOne('/api/admin/roles/1').flush('You cannot delete this role.', {
      status: 400,
      statusText: 'Bad Request',
    });

    await expect(promise).rejects.toBeTruthy();
  });
});
