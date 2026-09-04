import type { TestUser } from '../support/commands';

const plainUser: TestUser = { id: 2, username: 'alice', roles: [{ id: 2, name: 'USER_ROLE', permissions: [] }] };

describe('File browsing', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/files/home', { fixture: 'home.json' });
    cy.intercept('GET', { pathname: '/api/files', query: { parentUuid: 'home-uuid' } }, { fixture: 'files-root.json' }).as(
      'rootFiles',
    );
    cy.intercept('GET', { pathname: '/api/files', query: { parentUuid: 'folder-1' } }, {
      fixture: 'files-documents-folder.json',
    }).as('folderFiles');

    cy.loginAs(plainUser, '/files');
    cy.wait('@rootFiles');
  });

  it('lists folders and files with their names and sizes', () => {
    cy.contains('.grid-item', 'Documents').should('exist').and('contain.text', '(folder)');
    cy.contains('.grid-item', 'notes.txt').should('exist').and('contain.text', '2.0 KB');
  });

  it('shows the current location as a breadcrumb', () => {
    cy.get('nav[aria-label="Breadcrumb"]').should('contain.text', 'root');
  });

  it('navigates into a folder and back via the breadcrumb', () => {
    cy.contains('.grid-item', 'Documents').click();
    cy.wait('@folderFiles');

    cy.contains('.grid-item', 'report.pdf').should('exist');
    cy.get('nav[aria-label="Breadcrumb"]').should('contain.text', 'Documents');

    cy.contains('nav[aria-label="Breadcrumb"] *', 'root').click();
    cy.wait('@rootFiles');
    cy.contains('.grid-item', 'Documents').should('exist');
  });

  it('opens a folder with the keyboard (Enter) as well as a click', () => {
    cy.contains('.grid-item', 'Documents').closest('nz-card').focus().type('{enter}');
    cy.wait('@folderFiles');
    cy.contains('.grid-item', 'report.pdf').should('exist');
  });

  it('downloads a file', () => {
    cy.intercept('GET', '/api/files/download/file-1', {
      statusCode: 200,
      headers: { 'content-type': 'application/octet-stream' },
      body: 'file contents',
      delay: 200,
    }).as('download');

    cy.contains('.grid-item', 'notes.txt').rightclick();
    cy.get('.ant-dropdown-menu-item').contains('Download').click();
    cy.wait('@download');
  });

  it('deletes a file after confirmation', () => {
    cy.intercept('DELETE', '/api/files/file-1', '').as('deleteFile');

    cy.contains('.grid-item', 'notes.txt').rightclick();
    // The app uses a native confirm() dialog; Cypress auto-accepts it.
    cy.get('.ant-dropdown-menu-item').contains('Delete').click();
    cy.wait('@deleteFile');
    cy.contains('.grid-item', 'notes.txt').should('not.exist');
  });
});
