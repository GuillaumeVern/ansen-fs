import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { Files } from './files';
import { StoreService } from '../../services/store';
import { icons } from '../../icons-provider';

if (!(globalThis as any).ResizeObserver) {
  (globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

describe('Files', () => {
  let component: Files;
  let fixture: ComponentFixture<Files>;

  beforeEach(async () => {
    const storeServiceStub = {
      fileNodes: () => [],
      isLoading: () => false,
      ariane: () => [],
      isExhausted: () => false,
      currentParentUuid: () => undefined,
      initHome: vi.fn().mockResolvedValue(undefined),
      loadFiles: vi.fn().mockResolvedValue(true),
      goBack: vi.fn(),
      jumpToBreadcrumbIndex: vi.fn(),
      changeParent: vi.fn().mockResolvedValue(undefined),
      deleteItem: vi.fn().mockResolvedValue(true),
      downloadFile: vi.fn(),
      reset: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Files],
      providers: [provideNzIcons(icons), { provide: StoreService, useValue: storeServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(Files);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
