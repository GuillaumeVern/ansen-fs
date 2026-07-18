import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpEventType } from '@angular/common/http';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { StoreElement } from './store-element';
import { StoreService } from '../../../services/store';
import { FileNode } from '../store';
import { icons } from '../../../icons-provider';

describe('StoreElement', () => {
  let component: StoreElement;
  let fixture: ComponentFixture<StoreElement>;
  let storeServiceStub: { changeParent: ReturnType<typeof vi.fn>; downloadFile: ReturnType<typeof vi.fn> };

  const folderNode: FileNode = { uuid: 'f1', parentUuid: 'home', name: 'Docs', type: 'FOLDER', hash: null as any, size: 0 };
  const fileNode: FileNode = { uuid: 'file1', parentUuid: 'home', name: 'photo.jpg', type: 'FILE', hash: null as any, size: 1024 };

  beforeEach(async () => {
    storeServiceStub = {
      changeParent: vi.fn().mockResolvedValue(undefined),
      downloadFile: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [StoreElement],
      providers: [provideNzIcons(icons), { provide: StoreService, useValue: storeServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(StoreElement);
    fixture.componentRef.setInput('data', folderNode);
    component = fixture.componentInstance;
  });

  it('should create and render the item name', async () => {
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Docs');
  });

  it('open() navigates into folders', () => {
    component.open();
    expect(storeServiceStub.changeParent).toHaveBeenCalledWith('Docs', 'f1');
  });

  it('open() does nothing for files', () => {
    fixture.componentRef.setInput('data', fileNode);
    component.open();
    expect(storeServiceStub.changeParent).not.toHaveBeenCalled();
  });

  it('isSupportedImage recognizes common image extensions case-insensitively', () => {
    expect(component.isSupportedImage('photo.jpg')).toBe(true);
    expect(component.isSupportedImage('photo.PNG')).toBe(true);
    expect(component.isSupportedImage('document.pdf')).toBe(false);
    expect(component.isSupportedImage('')).toBe(false);
  });

  describe('download', () => {
    let createObjectURLSpy: ReturnType<typeof vi.fn>;
    let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      fixture.componentRef.setInput('data', fileNode);
      createObjectURLSpy = vi.fn().mockReturnValue('blob:mock');
      revokeObjectURLSpy = vi.fn();
      vi.stubGlobal('URL', { createObjectURL: createObjectURLSpy, revokeObjectURL: revokeObjectURLSpy });
    });

    afterEach(() => {
      vi.unstubAllGlobals();
    });

    it('tracks progress and triggers a browser download on completion', () => {
      const blob = new Blob(['data']);
      storeServiceStub.downloadFile.mockReturnValue(
        of(
          { type: HttpEventType.DownloadProgress, loaded: 50, total: 100 } as any,
          { type: HttpEventType.Response, body: blob } as any,
        ),
      );
      const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

      component.download();

      expect(component.isDownloading).toBe(false);
      expect(createObjectURLSpy).toHaveBeenCalledWith(blob);
      expect(clickSpy).toHaveBeenCalled();
      expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock');

      clickSpy.mockRestore();
    });

    it('resets state when the download errors', () => {
      storeServiceStub.downloadFile.mockReturnValue(throwError(() => new Error('network error')));

      component.download();

      expect(component.isDownloading).toBe(false);
    });
  });

  describe('onDeleteClick', () => {
    it('emits delete when the user confirms', () => {
      fixture.componentRef.setInput('data', fileNode);
      const emitSpy = vi.spyOn(component.delete, 'emit');
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      const event = new MouseEvent('click');
      const preventSpy = vi.spyOn(event, 'preventDefault');
      const stopSpy = vi.spyOn(event, 'stopPropagation');

      component.onDeleteClick(event);

      expect(preventSpy).toHaveBeenCalled();
      expect(stopSpy).toHaveBeenCalled();
      expect(emitSpy).toHaveBeenCalledWith('file1');
    });

    it('does not emit delete when the user cancels', () => {
      const emitSpy = vi.spyOn(component.delete, 'emit');
      vi.spyOn(window, 'confirm').mockReturnValue(false);

      component.onDeleteClick(new MouseEvent('click'));

      expect(emitSpy).not.toHaveBeenCalled();
    });
  });
});
