import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { Store } from './store';
import { StoreService } from '../../services/store';
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
      storeServiceStub.setNodes([{ uuid: '1', name: 'a', type: 'FILE', parentUuid: 'p', hash: null, size: 0 }]);
      await fixture.whenStable();
      fixture.detectChanges();
      storeServiceStub.loadFiles.mockClear();

      storeServiceStub.setLoading(true);
      component.onScrollIndexChange(0);

      expect(storeServiceStub.loadFiles).not.toHaveBeenCalled();
    });
  });
});
