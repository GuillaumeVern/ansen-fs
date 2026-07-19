import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpEventType } from '@angular/common/http';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { StoreElement } from './store-element';
import { FileNode, StoreService } from '../../../services/store';
import { icons } from '../../../icons-provider';

describe('StoreElement', () => {
  let component: StoreElement;
  let fixture: ComponentFixture<StoreElement>;
  let storeServiceStub: { changeParent: ReturnType<typeof vi.fn>; downloadFile: ReturnType<typeof vi.fn>; getPreview: ReturnType<typeof vi.fn> };
  let createObjectURLSpy: ReturnType<typeof vi.fn>;
  let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

  const folderNode: FileNode = { uuid: 'f1', parentUuid: 'home', name: 'Docs', type: 'FOLDER', hash: null as any, size: 0 };
  const imageNode: FileNode = { uuid: 'img1', parentUuid: 'home', name: 'photo.jpg', type: 'IMAGE', hash: null as any, size: 1024 };
  const videoNode: FileNode = { uuid: 'vid1', parentUuid: 'home', name: 'clip.mp4', type: 'VIDEO', hash: null as any, size: 2048 };
  const pdfNode: FileNode = { uuid: 'pdf1', parentUuid: 'home', name: 'report.pdf', type: 'PDF', hash: null as any, size: 512 };
  const textNode: FileNode = { uuid: 'txt1', parentUuid: 'home', name: 'notes.txt', type: 'TEXT', hash: null as any, size: 64 };

  beforeEach(async () => {
    storeServiceStub = {
      changeParent: vi.fn().mockResolvedValue(undefined),
      downloadFile: vi.fn(),
      getPreview: vi.fn().mockReturnValue(of(new Blob(['data']))),
    };

    let objectUrlCounter = 0;
    createObjectURLSpy = vi.fn().mockImplementation(() => `blob:mock-${++objectUrlCounter}`);
    revokeObjectURLSpy = vi.fn();
    vi.stubGlobal('URL', { createObjectURL: createObjectURLSpy, revokeObjectURL: revokeObjectURLSpy });

    await TestBed.configureTestingModule({
      imports: [StoreElement],
      providers: [provideNzIcons(icons), { provide: StoreService, useValue: storeServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(StoreElement);
    fixture.componentRef.setInput('data', folderNode);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
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
    fixture.componentRef.setInput('data', imageNode);
    component.open();
    expect(storeServiceStub.changeParent).not.toHaveBeenCalled();
  });

  describe('previewKind', () => {
    it('resolves IMAGE and VIDEO to the image preview kind', () => {
      fixture.componentRef.setInput('data', imageNode);
      expect(component.previewKind).toBe('image');

      fixture.componentRef.setInput('data', videoNode);
      expect(component.previewKind).toBe('image');
    });

    it('resolves PDF to the pdf preview kind', () => {
      fixture.componentRef.setInput('data', pdfNode);
      expect(component.previewKind).toBe('pdf');
    });

    it('resolves everything else to the icon preview kind', () => {
      fixture.componentRef.setInput('data', textNode);
      expect(component.previewKind).toBe('icon');

      fixture.componentRef.setInput('data', folderNode);
      expect(component.previewKind).toBe('icon');
    });
  });

  describe('fallbackIcon', () => {
    it('gives each type a distinct, sensible icon', () => {
      fixture.componentRef.setInput('data', folderNode);
      expect(component.fallbackIcon).toBe('folder');

      fixture.componentRef.setInput('data', imageNode);
      expect(component.fallbackIcon).toBe('file-image');

      fixture.componentRef.setInput('data', videoNode);
      expect(component.fallbackIcon).toBe('video-camera');

      fixture.componentRef.setInput('data', pdfNode);
      expect(component.fallbackIcon).toBe('file-pdf');

      fixture.componentRef.setInput('data', textNode);
      expect(component.fallbackIcon).toBe('file-text');
    });
  });

  describe('displaySize', () => {
    it('formats a byte count into a human-readable size', () => {
      fixture.componentRef.setInput('data', { ...imageNode, size: 1536 });
      expect(component.displaySize).toBe('1.5 KB');

      fixture.componentRef.setInput('data', { ...imageNode, size: 0 });
      expect(component.displaySize).toBe('0 B');

      fixture.componentRef.setInput('data', { ...imageNode, size: 5 * 1024 * 1024 });
      expect(component.displaySize).toBe('5.0 MB');
    });

    it('tags folder sizes distinctly from file sizes', () => {
      fixture.componentRef.setInput('data', { ...folderNode, size: 2 * 1024 * 1024 * 1024 });
      expect(component.displaySize).toBe('2.0 GB (folder)');
    });
  });

  describe('preview loading', () => {
    it('fetches the preview as a blob through the store service, keyed by uuid', () => {
      fixture.componentRef.setInput('data', imageNode);
      fixture.detectChanges();

      expect(storeServiceStub.getPreview).toHaveBeenCalledWith('img1');
      expect(createObjectURLSpy).toHaveBeenCalled();
    });

    it('produces a sanitizer-trusted resource url for iframe use once the blob resolves', () => {
      fixture.componentRef.setInput('data', pdfNode);
      fixture.detectChanges();

      expect(component.trustedPreviewUrl).toBeTruthy();
    });

    it('does not fetch a preview for non-previewable types', () => {
      fixture.componentRef.setInput('data', textNode);
      fixture.detectChanges();

      expect(storeServiceStub.getPreview).not.toHaveBeenCalled();
    });
  });

  describe('template rendering', () => {
    it('renders an <img> bound to the fetched blob object url for the image preview kind', async () => {
      fixture.componentRef.setInput('data', imageNode);
      await fixture.whenStable();
      fixture.detectChanges();

      const img = (fixture.nativeElement as HTMLElement).querySelector('img.preview-thumb');
      expect(img).toBeTruthy();
      expect(img?.getAttribute('src')).toBe('blob:mock-1');
    });

    it('renders an <iframe> for the pdf preview kind', async () => {
      fixture.componentRef.setInput('data', pdfNode);
      await fixture.whenStable();
      fixture.detectChanges();

      const iframe = (fixture.nativeElement as HTMLElement).querySelector('iframe.preview-pdf');
      expect(iframe).toBeTruthy();
    });

    it('renders a fallback icon for non-previewable types', async () => {
      fixture.componentRef.setInput('data', textNode);
      await fixture.whenStable();
      fixture.detectChanges();

      const placeholder = (fixture.nativeElement as HTMLElement).querySelector('.file-icon-placeholder');
      expect(placeholder).toBeTruthy();
      expect((fixture.nativeElement as HTMLElement).querySelector('img.preview-thumb')).toBeNull();
    });

    it('falls back to the icon when the image preview fails to load', async () => {
      fixture.componentRef.setInput('data', imageNode);
      await fixture.whenStable();
      fixture.detectChanges();

      const img = (fixture.nativeElement as HTMLElement).querySelector('img.preview-thumb');
      expect(img).toBeTruthy();

      img!.dispatchEvent(new Event('error'));
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).querySelector('img.preview-thumb')).toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('.file-icon-placeholder')).toBeTruthy();
    });

    it('falls back to the icon when the backend rejects the preview request (e.g. unauthorized)', async () => {
      storeServiceStub.getPreview.mockReturnValue(throwError(() => new Error('403 Forbidden')));

      fixture.componentRef.setInput('data', imageNode);
      await fixture.whenStable();
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).querySelector('img.preview-thumb')).toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('.file-icon-placeholder')).toBeTruthy();
    });
  });

  describe('download', () => {
    let createObjectURLSpy: ReturnType<typeof vi.fn>;
    let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      fixture.componentRef.setInput('data', imageNode);
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
      fixture.componentRef.setInput('data', imageNode);
      const emitSpy = vi.spyOn(component.delete, 'emit');
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      const event = new MouseEvent('click');
      const preventSpy = vi.spyOn(event, 'preventDefault');
      const stopSpy = vi.spyOn(event, 'stopPropagation');

      component.onDeleteClick(event);

      expect(preventSpy).toHaveBeenCalled();
      expect(stopSpy).toHaveBeenCalled();
      expect(emitSpy).toHaveBeenCalledWith('img1');
    });

    it('does not emit delete when the user cancels', () => {
      const emitSpy = vi.spyOn(component.delete, 'emit');
      vi.spyOn(window, 'confirm').mockReturnValue(false);

      component.onDeleteClick(new MouseEvent('click'));

      expect(emitSpy).not.toHaveBeenCalled();
    });
  });
});
