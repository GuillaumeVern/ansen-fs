import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNzIcons } from 'ng-zorro-antd/icon';
import { vi } from 'vitest';

import { Upload } from './upload';
import { StoreService } from '../../services/store';
import { icons } from '../../icons-provider';

describe('Upload', () => {
  let component: Upload;
  let fixture: ComponentFixture<Upload>;
  let httpMock: HttpTestingController;
  let storeServiceStub: { currentParentUuid: () => string; loadFiles: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    storeServiceStub = {
      currentParentUuid: () => 'parent-uuid',
      loadFiles: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Upload],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNzIcons(icons),
        { provide: StoreService, useValue: storeServiceStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Upload);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('onDragOver sets the dragging flag and swallows the event', () => {
    const event = new Event('dragover') as DragEvent;
    const preventSpy = vi.spyOn(event, 'preventDefault');
    const stopSpy = vi.spyOn(event, 'stopPropagation');

    component.onDragOver(event);

    expect((component as any).isDraggingOver).toBe(true);
    expect(preventSpy).toHaveBeenCalled();
    expect(stopSpy).toHaveBeenCalled();
  });

  it('onDragLeave clears the dragging flag', () => {
    (component as any).isDraggingOver = true;
    component.onDragLeave(new Event('dragleave') as DragEvent);
    expect((component as any).isDraggingOver).toBe(false);
  });

  it('cancelDragOverlay clears the dragging flag and swallows the event', () => {
    (component as any).isDraggingOver = true;
    const event = new MouseEvent('click');
    const preventSpy = vi.spyOn(event, 'preventDefault');

    component.cancelDragOverlay(event);

    expect((component as any).isDraggingOver).toBe(false);
    expect(preventSpy).toHaveBeenCalled();
  });

  it('onItemsSelected captures the chosen files', () => {
    const file = new File(['data'], 'a.txt');
    component.onItemsSelected({ target: { files: [file] } });
    expect(component.selectedFiles).toEqual([file]);
  });

  it('uploadItems does nothing when no files are selected', () => {
    component.selectedFiles = [];
    component.uploadItems();
    httpMock.expectNone((r) => r.url === '/api/files/jobs/new');
  });

  it('uploadItems posts a manifest, uploads files in chunks, then refreshes the listing', async () => {
    const files = [1, 2, 3, 4].map((i) => new File([`data${i}`], `f${i}.txt`));
    component.selectedFiles = files;

    component.uploadItems();

    const jobReq = httpMock.expectOne('/api/files/jobs/new');
    expect(jobReq.request.body).toEqual({
      parentUuid: 'parent-uuid',
      totalFiles: 4,
      manifest: ['f1.txt', 'f2.txt', 'f3.txt', 'f4.txt'],
    });
    jobReq.flush({ jobId: 'job-1' });

    const chunk1 = httpMock.expectOne('api/files/jobs/job-1/upload');
    chunk1.flush({});
    const chunk2 = httpMock.expectOne('api/files/jobs/job-1/upload');
    chunk2.flush({});

    await fixture.whenStable();

    expect(component.selectedFiles).toEqual([]);
    expect(storeServiceStub.loadFiles).toHaveBeenCalledWith(true);
  });

  it('triggerFileInput clicks the hidden file input', () => {
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[type=file]:not([webkitdirectory])') as HTMLInputElement;
    const clickSpy = vi.spyOn(input, 'click').mockImplementation(() => {});

    component.triggerFileInput();

    expect(clickSpy).toHaveBeenCalled();
  });

  it('triggerFolderInput clicks the hidden folder input', () => {
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[webkitdirectory]') as HTMLInputElement;
    const clickSpy = vi.spyOn(input, 'click').mockImplementation(() => {});

    component.triggerFolderInput();

    expect(clickSpy).toHaveBeenCalled();
  });

  it('onFileDrop reads a dropped file and starts the upload', async () => {
    component.selectedFiles = [];
    const droppedFile = new File(['abc'], 'dropped.txt');

    const fakeEntry = {
      isFile: true,
      isDirectory: false,
      name: 'dropped.txt',
      file: (cb: (f: File) => void) => cb(droppedFile),
    };

    const event = {
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      dataTransfer: {
        items: [{ kind: 'file', webkitGetAsEntry: () => fakeEntry }],
      },
    } as unknown as DragEvent;

    await component.onFileDrop(event);

    expect(component.selectedFiles.map((f) => f.name)).toEqual(['dropped.txt']);

    const jobReq = httpMock.expectOne('/api/files/jobs/new');
    jobReq.flush({ jobId: 'job-2' });
    const chunkReq = httpMock.expectOne('api/files/jobs/job-2/upload');
    chunkReq.flush({});
    await fixture.whenStable();
  });

  it('onFileDrop ignores non-file drag items', async () => {
    component.selectedFiles = [];
    const event = {
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      dataTransfer: { items: [{ kind: 'string', webkitGetAsEntry: () => null }] },
    } as unknown as DragEvent;

    await component.onFileDrop(event);

    expect(component.selectedFiles).toEqual([]);
    httpMock.expectNone((r) => r.url === '/api/files/jobs/new');
  });
});
