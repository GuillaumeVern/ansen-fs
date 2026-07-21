describe('Login', () => {
  beforeEach(() => {
    cy.visit('/login');
  });

  it('has a labelled username/password form and keeps the submit button disabled until valid', () => {
    cy.get('label[for="login-username"]').should('contain.text', 'Username');
    cy.get('label[for="login-password"]').should('contain.text', 'Password');

    cy.contains('button', 'Sign in').should('be.disabled');

    cy.get('#login-username').type('alice');
    cy.contains('button', 'Sign in').should('be.disabled');

    cy.get('#login-password').type('secret');
    cy.contains('button', 'Sign in').should('not.be.disabled');
  });

  it('logs in and redirects to the files page on success', () => {
    cy.intercept('POST', '/api/auth/login', { statusCode: 200, body: { token: 'test-token' } }).as('login');
    cy.intercept('GET', '/api/auth/me', { fixture: 'user-plain.json' }).as('me');
    cy.intercept('GET', '/api/files/home', { fixture: 'home.json' });
    cy.intercept('GET', { pathname: '/api/files', query: { parentUuid: 'home-uuid' } }, { body: [] });

    cy.get('#login-username').type('alice');
    cy.get('#login-password').type('secret');
    cy.contains('button', 'Sign in').click();

    cy.wait('@login');
    cy.wait('@me');
    cy.location('pathname').should('eq', '/files');
  });

  it('shows an announced error message on invalid credentials and stays on the page', () => {
    cy.intercept('POST', '/api/auth/login', { statusCode: 401, body: { message: 'bad credentials' } }).as('login');

    cy.get('#login-username').type('alice');
    cy.get('#login-password').type('wrong');
    cy.contains('button', 'Sign in').click();

    cy.wait('@login');
    cy.get('[role="alert"]').should('be.visible').and('contain.text', 'Invalid username or password.');
    cy.location('pathname').should('eq', '/login');
  });
});

describe('Route guards', () => {
  it('redirects an unauthenticated visitor from /files to /login', () => {
    cy.visit('/files');
    cy.location('pathname').should('eq', '/login');
  });

  it('redirects an unauthenticated visitor from /admin to /login', () => {
    cy.visit('/admin');
    cy.location('pathname').should('eq', '/login');
  });
});
