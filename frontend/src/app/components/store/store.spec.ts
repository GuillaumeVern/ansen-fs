import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpEventType } from '@angular/common/http';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Store } from './store';
import { FileNode, StoreService } from '../../services/store';
import { icons } from '../../icons-provider';

if (!(globalThis as any).ResizeObserver) {
  (globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

function createStoreServiceStub() {
  let nodes: any[] = [];
  let loading = false;

  return {
    fileNodes: () => nodes,
    isLoading: () => loading,
    ariane: () => [] as string[],
    isExhausted: () => false,
    currentParentUuid: () => undefined as string | undefined,
    initHome: vi.fn().mockResolvedValue(undefined),
    loadFiles: vi.fn().mockResolvedValue(true),
    goBack: vi.fn(),
    jumpToBreadcrumbIndex: vi.fn(),
    changeParent: vi.fn().mockResolvedValue(undefined),
    deleteItem: vi.fn().mockResolvedValue(true),
    downloadFile: vi.fn(),
    reset: vi.fn(),
    setNodes: (n: any[]) => (nodes = n),
    setLoading: (v: boolean) => (loading = v),
  };
}

describe('Store', () => {
  let component: Store;
  let fixture: ComponentFixture<Store>;
  let storeServiceStub: ReturnType<typeof createStoreServiceStub>;

  beforeEach(async () => {
    storeServiceStub = createStoreServiceStub();

    await TestBed.configureTestingModule({
      imports: [Store],
      providers: [provideNzIcons(icons), { provide: StoreService, useValue: storeServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(Store);
    component = fixture.componentInstance;
  });

  it('should create and render without a container size', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('ngOnInit initializes the home folder then loads the first page', async () => {
    await component.ngOnInit();

    expect(storeServiceStub.initHome).toHaveBeenCalled();
    expect(storeServiceStub.loadFiles).toHaveBeenCalledWith(true);
  });

  it('goBack delegates to the store service', () => {
    component.goBack();
    expect(storeServiceStub.goBack).toHaveBeenCalled();
  });

  it('navigateToIndex delegates to jumpToBreadcrumbIndex', () => {
    component.navigateToIndex(2);
    expect(storeServiceStub.jumpToBreadcrumbIndex).toHaveBeenCalledWith(2);
  });

  it('trackByRow keys by the first item uuid, falling back to the row index', () => {
    expect(component.trackByRow(3, [{ uuid: 'abc' }])).toBe('abc');
    expect(component.trackByRow(3, [])).toBe(3);
  });

  describe('deleteItem', () => {
    it('does not alert when deletion succeeds', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      storeServiceStub.deleteItem.mockResolvedValue(true);

      component.deleteItem('uuid-1');
      await Promise.resolve();
      await Promise.resolve();

      expect(alertSpy).not.toHaveBeenCalled();
      alertSpy.mockRestore();
    });

    it('alerts the user when deletion fails', async () => {
      const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
      storeServiceStub.deleteItem.mockResolvedValue(false);

      component.deleteItem('uuid-1');
      await Promise.resolve();
      await Promise.resolve();

      expect(alertSpy).toHaveBeenCalled();
      alertSpy.mockRestore();
    });
  });

  describe('onScrollIndexChange', () => {
    it('does nothing when there are no rows', async () => {
      // fileNodes() is read inside a computed(); leave it empty from the very first
      // render so the computed never caches a non-empty result to begin with.
      await fixture.whenStable();
      fixture.detectChanges();
      storeServiceStub.loadFiles.mockClear();

      component.onScrollIndexChange(0);

      expect(storeServiceStub.loadFiles).not.toHaveBeenCalled();
    });

    it('does nothing while already loading', async () => {
      storeServiceStub.setNodes([{ uuid: '1', name: 'a', type: 'TEXT', parentUuid: 'p', hash: null, size: 0 }]);
      await fixture.whenStable();
      fixture.detectChanges();
      storeServiceStub.loadFiles.mockClear();

      storeServiceStub.setLoading(true);
      component.onScrollIndexChange(0);

      expect(storeServiceStub.loadFiles).not.toHaveBeenCalled();
    });
  });

  describe('list view', () => {
    const folderNode: FileNode = { uuid: 'f1', parentUuid: 'home', name: 'Docs', type: 'FOLDER', hash: null as any, size: 0 };
    const fileNode: FileNode = { uuid: 'img1', parentUuid: 'home', name: 'photo.jpg', type: 'IMAGE', hash: null as any, size: 1536 };

    it('toggleListView switches the view mode', () => {
      expect((component as any).isListView()).toBe(false);
      component.toggleListView(true);
      expect((component as any).isListView()).toBe(true);
      component.toggleListView(false);
      expect((component as any).isListView()).toBe(false);
    });

    it('loadMore requests the next page', () => {
      component.loadMore();
      expect(storeServiceStub.loadFiles).toHaveBeenCalledWith(false);
    });

    it('openRow navigates into folders but does nothing for files', () => {
      component.openRow(folderNode);
      expect(storeServiceStub.changeParent).toHaveBeenCalledWith('Docs', 'f1');

      storeServiceStub.changeParent.mockClear();
      component.openRow(fileNode);
      expect(storeServiceStub.changeParent).not.toHaveBeenCalled();
    });

    describe('onRowKeydown', () => {
      it('opens a folder row on Enter/Space', () => {
        const event = new KeyboardEvent('keydown', { key: 'Enter' });
        const preventSpy = vi.spyOn(event, 'preventDefault');

        component.onRowKeydown(event, folderNode);

        expect(preventSpy).toHaveBeenCalled();
        expect(storeServiceStub.changeParent).toHaveBeenCalledWith('Docs', 'f1');
      });

      it('does nothing for a file row', () => {
        component.onRowKeydown(new KeyboardEvent('keydown', { key: 'Enter' }), fileNode);
        expect(storeServiceStub.changeParent).not.toHaveBeenCalled();
      });
    });

    it('typeIcon and displaySize match the grid view formatting', () => {
      expect(component.typeIcon(folderNode)).toBe('folder');
      expect(component.typeIcon(fileNode)).toBe('file-image');

      expect(component.displaySize({ ...folderNode, size: 2048 })).toBe('2.0 KB (folder)');
      expect(component.displaySize({ ...fileNode, size: 1536 })).toBe('1.5 KB');
    });

    describe('downloadNode', () => {
      let createObjectURLSpy: ReturnType<typeof vi.fn>;
      let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

      beforeEach(() => {
        createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock') as any;
        revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {}) as any;
      });

      afterEach(() => {
        vi.restoreAllMocks();
      });

      it('tracks the downloading uuid and clears it on completion', () => {
        const blob = new Blob(['data']);
        storeServiceStub.downloadFile.mockReturnValue(
          of(
            { type: HttpEventType.DownloadProgress, loaded: 50, total: 100 } as any,
            { type: HttpEventType.Response, body: blob } as any,
          ),
        );
        const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

        expect(component.isDownloading('img1')).toBe(false);
        component.downloadNode(fileNode);

        expect(createObjectURLSpy).toHaveBeenCalledWith(blob);
        expect(clickSpy).toHaveBeenCalled();
        expect(component.isDownloading('img1')).toBe(false);

        clickSpy.mockRestore();
      });

      it('clears the downloading uuid on error', () => {
        storeServiceStub.downloadFile.mockReturnValue(throwError(() => new Error('network error')));

        component.downloadNode(fileNode);

        expect(component.isDownloading('img1')).toBe(false);
      });
    });

    describe('onDeleteRowClick', () => {
      it('does nothing when there is no active node', () => {
        component.onDeleteRowClick(null);
        expect(storeServiceStub.deleteItem).not.toHaveBeenCalled();
      });

      it('deletes the node when the user confirms', () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        component.onDeleteRowClick(fileNode);
        expect(storeServiceStub.deleteItem).toHaveBeenCalledWith('img1');
      });

      it('does not delete when the user cancels', () => {
        vi.spyOn(window, 'confirm').mockReturnValue(false);
        component.onDeleteRowClick(fileNode);
        expect(storeServiceStub.deleteItem).not.toHaveBeenCalled();
      });
    });

    it('openContextMenu records the active node', () => {
      const menu = { descendantMenuItemClick$: of(void 0) } as any;
      vi.spyOn((component as any).nzContextMenuService, 'create').mockImplementation(() => {});

      component.openContextMenu(new MouseEvent('contextmenu'), menu, fileNode);

      expect((component as any).activeNode).toBe(fileNode);
    });
  });
});
