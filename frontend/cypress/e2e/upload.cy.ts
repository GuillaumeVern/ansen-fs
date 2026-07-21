import type { TestUser } from '../support/commands';

const plainUser: TestUser = { id: 2, username: 'alice', roles: [{ id: 2, name: 'USER_ROLE', permissions: [] }] };

describe('Upload', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/files/home', { fixture: 'home.json' });
    cy.intercept('GET', { pathname: '/api/files', query: { parentUuid: 'home-uuid' } }, { body: [] }).as('list');

    cy.loginAs(plainUser, '/files');
    cy.wait('@list');
  });

  it('uploads a single file chosen via the "Upload Files" button and shows live progress', () => {
    cy.intercept('POST', '/api/files/jobs/new', { statusCode: 200, body: { jobId: 'job-1' } }).as('newJob');
    cy.intercept('POST', '/api/files/jobs/job-1/upload', { statusCode: 200, body: {}, delay: 300 }).as('uploadChunk');

    cy.contains('button', 'Upload Files').click();
    cy.get('input[type=file]:not([webkitdirectory])').selectFile(
      {
        contents: Cypress.Buffer.from('hello world'),
        fileName: 'hello.txt',
        mimeType: 'text/plain',
      },
      { force: true },
    );

    cy.wait('@newJob');

    // While the (artificially delayed) upload request is in flight, the global transfer tray
    // should announce progress for this exact file.
    cy.contains('app-transfer-tray', 'Uploading').should('be.visible');
    cy.contains('app-transfer-tray', 'hello.txt').should('be.visible');

    cy.wait('@uploadChunk');
    cy.contains('app-transfer-tray', 'Uploading').should('not.exist');
  });

  it('uploads multiple files chosen via the "Upload Folder" button and shows batch progress', () => {
    cy.intercept('POST', '/api/files/jobs/new', { statusCode: 200, body: { jobId: 'job-2' } }).as('newJob');
    cy.intercept('POST', '/api/files/jobs/job-2/upload', { statusCode: 200, body: {}, delay: 200 }).as('uploadChunk');

    cy.contains('button', 'Upload Folder').click();
    cy.get('input[webkitdirectory]').selectFile(
      [
        { contents: Cypress.Buffer.from('a'), fileName: 'a.txt', mimeType: 'text/plain' },
        { contents: Cypress.Buffer.from('b'), fileName: 'b.txt', mimeType: 'text/plain' },
      ],
      { force: true },
    );

    cy.wait('@newJob');
    cy.contains('app-transfer-tray', /\d+ \/ 2 files/).should('be.visible');

    cy.wait('@uploadChunk');
    cy.wait('@uploadChunk');
    cy.contains('app-transfer-tray', 'Uploading').should('not.exist');
  });
});
