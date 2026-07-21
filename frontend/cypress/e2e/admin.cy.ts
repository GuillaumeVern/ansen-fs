import type { TestUser } from '../support/commands';

const plainUser: TestUser = { id: 2, username: 'alice', roles: [{ id: 2, name: 'USER_ROLE', permissions: [] }] };
const adminUser: TestUser = { id: 1, username: 'admin', roles: [{ id: 1, name: 'ADMIN', permissions: [] }] };

describe('Admin access control', () => {
  it('hides the Admin nav link for a non-admin user and redirects away from /admin', () => {
    cy.intercept('GET', '/api/files/home', { fixture: 'home.json' });
    cy.intercept('GET', { pathname: '/api/files', query: { parentUuid: 'home-uuid' } }, { body: [] });

    cy.loginAs(plainUser, '/files');
    cy.contains('a', 'Admin').should('not.exist');

    cy.visit('/admin');
    cy.location('pathname').should('eq', '/files');
  });

  it('shows the Admin nav link for an admin user and grants access to /admin', () => {
    cy.intercept('GET', '/api/admin/users', { fixture: 'admin-users.json' });
    cy.intercept('GET', '/api/admin/roles', { fixture: 'admin-roles.json' });
    cy.intercept('GET', '/api/admin/permissions', { fixture: 'admin-permissions.json' });

    cy.loginAs(adminUser, '/admin');

    cy.location('pathname').should('eq', '/admin');
    cy.contains('h1', 'Admin').should('be.visible');
    cy.contains('a', 'Admin').should('exist');
  });
});

describe('Admin - Users panel', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/admin/users', { fixture: 'admin-users.json' }).as('users');
    cy.intercept('GET', '/api/admin/roles', { fixture: 'admin-roles.json' }).as('roles');
    cy.intercept('GET', '/api/admin/permissions', { fixture: 'admin-permissions.json' }).as('permissions');

    cy.loginAs(adminUser, '/admin');
    cy.wait(['@users', '@roles', '@permissions']);
  });

  it('lists existing users with their roles', () => {
    cy.contains('td', 'alice').should('exist');
    cy.contains('tr', 'alice').should('contain.text', 'USER_ROLE');
  });

  it('creates a new user through the modal', () => {
    cy.intercept('POST', '/api/users/create', { statusCode: 200 }).as('createUser');
    cy.intercept('GET', '/api/admin/users', { fixture: 'admin-users.json' }).as('usersReload');

    cy.contains('button', 'New user').click();
    cy.get('#new-user-username').type('carol');
    cy.get('#new-user-password').type('secretpw');
    cy.get('.ant-modal-footer').contains('button', 'OK').click();

    cy.wait('@createUser').its('request.body').should('deep.equal', { username: 'carol', password: 'secretpw' });
    cy.wait('@usersReload');
  });

  it('deletes a user after confirmation', () => {
    cy.intercept('DELETE', '/api/admin/users/2', '').as('deleteUser');

    cy.contains('tr', 'alice').find('button[aria-label="Delete user alice"]').click();
    cy.get('.ant-popover').contains('button', 'OK').click();

    cy.wait('@deleteUser');
    cy.contains('td', 'alice').should('not.exist');
  });
});

describe('Admin - Roles panel', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/admin/users', { fixture: 'admin-users.json' });
    cy.intercept('GET', '/api/admin/roles', { fixture: 'admin-roles.json' }).as('roles');
    cy.intercept('GET', '/api/admin/permissions', { fixture: 'admin-permissions.json' });

    cy.loginAs(adminUser, '/admin');
    cy.wait('@roles');
    cy.contains('.ant-tabs-tab', 'Roles').click();
  });

  it('lists existing roles with their permissions', () => {
    cy.contains('td', 'ADMIN').should('exist');
    cy.contains('tr', 'USER_ROLE').should('contain.text', 'READ');
  });

  it('prevents deleting a built-in role', () => {
    cy.contains('tr', 'ADMIN').find('button[aria-label*="built-in roles cannot be deleted"]').should('be.disabled');
  });

  it('creates a new role through the modal', () => {
    cy.intercept('POST', '/api/admin/roles', {
      statusCode: 200,
      body: { id: 3, name: 'AUDITOR', permissions: [] },
    }).as('createRole');

    cy.contains('button', 'New role').click();
    cy.get('#role-name').type('AUDITOR');
    cy.get('.ant-modal-footer').contains('button', 'OK').click();

    cy.wait('@createRole').its('request.body').should('deep.equal', { name: 'AUDITOR', permissionIds: [] });
  });
});

describe('Admin - Permissions panel', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/admin/users', { fixture: 'admin-users.json' });
    cy.intercept('GET', '/api/admin/roles', { fixture: 'admin-roles.json' });
    cy.intercept('GET', '/api/admin/permissions', { fixture: 'admin-permissions.json' }).as('permissions');

    cy.loginAs(adminUser, '/admin');
    cy.wait('@permissions');
    cy.contains('.ant-tabs-tab', 'Permissions').click();
  });

  it('lists existing permissions', () => {
    cy.contains('td', 'READ').should('exist');
    cy.contains('td', 'WRITE').should('exist');
  });

  it('adds a new permission', () => {
    cy.intercept('POST', '/api/admin/permissions', {
      statusCode: 200,
      body: { id: 3, name: 'DELETE' },
    }).as('createPermission');

    cy.get('#new-permission-name').type('DELETE');
    cy.contains('button', 'Add permission').click();

    cy.wait('@createPermission').its('request.body').should('deep.equal', { name: 'DELETE' });
  });

  it('deletes a permission after confirmation', () => {
    cy.intercept('DELETE', '/api/admin/permissions/1', '').as('deletePermission');

    cy.contains('tr', 'READ').find('button[aria-label="Delete permission READ"]').click();
    cy.get('.ant-popover').contains('button', 'OK').click();

    cy.wait('@deletePermission');
    cy.contains('td', 'READ').should('not.exist');
  });
});
