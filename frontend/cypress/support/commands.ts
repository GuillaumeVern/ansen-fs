export interface TestUser {
  id: number;
  username: string;
  roles: { id: number; name: string; permissions: unknown[] }[];
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /**
       * Seeds localStorage with a fake auth token before the app boots, stubs GET
       * /api/auth/me to resolve as the given user, then visits `url`. This mirrors what a
       * real login leaves behind, without needing the actual backend/login flow.
       */
      loginAs(user: TestUser, url?: string): Chainable<void>;
    }
  }
}

Cypress.Commands.add('loginAs', (user: TestUser, url = '/files') => {
  cy.intercept('GET', '/api/auth/me', { statusCode: 200, body: user }).as('me');

  cy.visit(url, {
    onBeforeLoad(win) {
      win.localStorage.setItem('anzenfs.token', 'test-token');
    },
  });

  cy.wait('@me');
});

export {};
