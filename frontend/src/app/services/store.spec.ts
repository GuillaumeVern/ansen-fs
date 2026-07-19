import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { FileNode, StoreService } from './store';

function node(uuid: string, name: string, parentUuid = 'home-uuid'): FileNode {
  return { uuid, parentUuid, name, type: 'TEXT', hash: null as unknown as string, size: 0 };
}

describe('StoreService', () => {
  let service: StoreService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StoreService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('is created with empty state', () => {
    expect(service).toBeTruthy();
    expect(service.fileNodes()).toEqual([]);
    expect(service.isLoading()).toBe(false);
    expect(service.isExhausted()).toBe(false);
  });

  describe('initHome', () => {
    it('seeds the breadcrumb and uuid history from the home folder', async () => {
      const promise = service.initHome();

      const req = httpMock.expectOne('/api/files/home');
      expect(req.request.method).toBe('GET');
      req.flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });

      await promise;

      expect(service.ariane()).toEqual(['alice']);
      expect(service.currentParentUuid()).toBe('home-uuid');
    });
  });

  describe('loadFiles', () => {
    it('replaces the node list and marks exhausted when the page is short', async () => {
      const promise = service.loadFiles(true);

      const req = httpMock.expectOne((r) => r.url === '/api/files');
      req.flush([node('a', 'a.txt'), node('b', 'b.txt')]);

      const result = await promise;

      expect(result).toBe(true);
      expect(service.fileNodes().map((n) => n.uuid)).toEqual(['a', 'b']);
      expect(service.isLoading()).toBe(false);
    });

    it('marks exhausted and clears nodes on an empty replace response', async () => {
      const promise = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([]);

      await promise;

      expect(service.fileNodes()).toEqual([]);
      expect(service.isExhausted()).toBe(true);
    });

    it('appends new nodes and sends lastFileName cursor on subsequent pages', async () => {
      const first = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([node('a', 'a.txt')]);
      await first;

      const second = service.loadFiles(false);
      const req = httpMock.expectOne((r) => r.url === '/api/files');
      expect(req.request.params.get('lastFileName')).toBe('a.txt');
      req.flush([node('b', 'b.txt')]);
      await second;

      expect(service.fileNodes().map((n) => n.uuid)).toEqual(['a', 'b']);
    });

    it('does not refetch once exhausted unless replace is requested', async () => {
      const first = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([]);
      await first;

      expect(service.isExhausted()).toBe(true);

      const result = await service.loadFiles(false);
      httpMock.expectNone((r) => r.url === '/api/files');
      expect(result).toBe(true);
    });

    it('ignores a concurrent call while a load is already in flight', async () => {
      const first = service.loadFiles(true);
      expect(service.isLoading()).toBe(true);

      const second = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([node('a', 'a.txt')]);

      await Promise.all([first, second]);
      httpMock.expectNone((r) => r.url === '/api/files');
    });

    it('returns false and stops loading when the request fails', async () => {
      const promise = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush('boom', { status: 500, statusText: 'Server Error' });

      const result = await promise;

      expect(result).toBe(false);
      expect(service.isLoading()).toBe(false);
    });

    it('discards a stale in-flight response after reset() bumps the request generation', async () => {
      const stalePromise = service.loadFiles(true);
      const staleReq = httpMock.expectOne((r) => r.url === '/api/files');

      service.reset();
      staleReq.flush([node('stale', 'stale.txt')]);

      await stalePromise;

      expect(service.fileNodes()).toEqual([]);
      expect(service.isLoading()).toBe(false);
    });
  });

  describe('navigation', () => {
    it('changeParent pushes a breadcrumb and loads the new folder', async () => {
      const initPromise = service.initHome();
      httpMock.expectOne('/api/files/home').flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });
      await initPromise;

      const promise = service.changeParent('docs', 'docs-uuid');
      const req = httpMock.expectOne((r) => r.url === '/api/files');
      expect(req.request.params.get('parentUuid')).toBe('docs-uuid');
      req.flush([node('f1', 'f1.txt', 'docs-uuid')]);
      await promise;

      expect(service.ariane()).toEqual(['alice', 'docs']);
      expect(service.currentParentUuid()).toBe('docs-uuid');
    });

    it('changeParent rolls back the breadcrumb when the load fails', async () => {
      const initPromise = service.initHome();
      httpMock.expectOne('/api/files/home').flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });
      await initPromise;

      const promise = service.changeParent('docs', 'docs-uuid');
      httpMock.expectOne((r) => r.url === '/api/files').flush('boom', { status: 500, statusText: 'Server Error' });
      await promise;

      expect(service.ariane()).toEqual(['alice']);
      expect(service.currentParentUuid()).toBe('home-uuid');
    });

    it('goBack pops the last breadcrumb and reloads the parent', async () => {
      const initPromise = service.initHome();
      httpMock.expectOne('/api/files/home').flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });
      await initPromise;

      const changePromise = service.changeParent('docs', 'docs-uuid');
      httpMock.expectOne((r) => r.url === '/api/files').flush([]);
      await changePromise;

      service.goBack();
      const req = httpMock.expectOne((r) => r.url === '/api/files');
      expect(req.request.params.get('parentUuid')).toBe('home-uuid');
      req.flush([]);

      expect(service.ariane()).toEqual(['alice']);
    });

    it('goBack is a no-op at the root of the history', () => {
      service.goBack();
      httpMock.expectNone((r) => r.url === '/api/files');
    });

    it('jumpToBreadcrumbIndex truncates history and reloads that folder', async () => {
      const initPromise = service.initHome();
      httpMock.expectOne('/api/files/home').flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });
      await initPromise;

      const changePromise = service.changeParent('docs', 'docs-uuid');
      httpMock.expectOne((r) => r.url === '/api/files').flush([]);
      await changePromise;

      service.jumpToBreadcrumbIndex(0);
      const req = httpMock.expectOne((r) => r.url === '/api/files');
      req.flush([]);

      expect(service.ariane()).toEqual(['alice']);
      expect(service.currentParentUuid()).toBe('home-uuid');
    });

    it('jumpToBreadcrumbIndex ignores an out-of-range index', async () => {
      const initPromise = service.initHome();
      httpMock.expectOne('/api/files/home').flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });
      await initPromise;

      service.jumpToBreadcrumbIndex(5);
      httpMock.expectNone((r) => r.url === '/api/files');
    });
  });

  describe('deleteItem', () => {
    it('removes the node from state on success', async () => {
      const load = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([node('a', 'a.txt'), node('b', 'b.txt')]);
      await load;

      const promise = service.deleteItem('a');
      const req = httpMock.expectOne('/api/files/a');
      expect(req.request.method).toBe('DELETE');
      req.flush('ok');

      const result = await promise;
      expect(result).toBe(true);
      expect(service.fileNodes().map((n) => n.uuid)).toEqual(['b']);
    });

    it('leaves state untouched and returns false on failure', async () => {
      const load = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([node('a', 'a.txt')]);
      await load;

      const promise = service.deleteItem('a');
      httpMock.expectOne('/api/files/a').flush('boom', { status: 500, statusText: 'Server Error' });

      const result = await promise;
      expect(result).toBe(false);
      expect(service.fileNodes().map((n) => n.uuid)).toEqual(['a']);
    });
  });

  describe('downloadFile', () => {
    it('issues a blob GET request with progress events observed', () => {
      service.downloadFile('doc-uuid').subscribe();

      const req = httpMock.expectOne('/api/files/download/doc-uuid');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['data']));
    });
  });

  describe('getPreview', () => {
    it('issues a blob GET request through HttpClient so the auth interceptor applies', () => {
      service.getPreview('img-uuid').subscribe();

      const req = httpMock.expectOne('/api/files/preview/img-uuid');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['data']));
    });
  });

  describe('reset', () => {
    it('clears all navigation and listing state', async () => {
      const initPromise = service.initHome();
      httpMock.expectOne('/api/files/home').flush({ uuid: 'home-uuid', parentUuid: null, name: 'alice', type: 'FOLDER', hash: null, size: 0 });
      await initPromise;

      const load = service.loadFiles(true);
      httpMock.expectOne((r) => r.url === '/api/files').flush([node('a', 'a.txt')]);
      await load;

      service.reset();

      expect(service.fileNodes()).toEqual([]);
      expect(service.ariane()).toEqual([]);
      expect(service.currentParentUuid()).toBeUndefined();
      expect(service.isLoading()).toBe(false);
      expect(service.isExhausted()).toBe(false);
    });
  });
});
