import {ChangeDetectorRef, Component, inject, Input, OnChanges, OnDestroy, output, SimpleChanges} from '@angular/core';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';
import {FileNode, FileType, StoreService} from '../../../services/store';
import {NzCardComponent, NzCardMetaComponent} from 'ng-zorro-antd/card';
import {NzButtonModule} from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import {HttpEvent, HttpEventType} from '@angular/common/http';
import {DecimalPipe} from '@angular/common';

type PreviewKind = 'image' | 'pdf' | 'icon';

/**
 * Which widget renders a file's preview, keyed by its classified type. Video resolves
 * to 'image' too: the backend serves a generated thumbnail frame for video through the
 * same preview endpoint, so from here it's indistinguishable from a still image.
 * Adding preview support for a new type is a one-line change here.
 */
const PREVIEW_KIND: Record<FileType, PreviewKind> = {
  FOLDER: 'icon',
  IMAGE: 'image',
  VIDEO: 'image',
  AUDIO: 'icon',
  PDF: 'pdf',
  DOCUMENT: 'icon',
  ARCHIVE: 'icon',
  TEXT: 'icon',
  OTHER: 'icon',
};

/** Icon shown for the 'icon' preview kind, and as the fallback when a live preview fails to load. */
const TYPE_ICON: Record<FileType, string> = {
  FOLDER: 'folder',
  IMAGE: 'file-image',
  VIDEO: 'video-camera',
  AUDIO: 'sound',
  PDF: 'file-pdf',
  DOCUMENT: 'file-text',
  ARCHIVE: 'file-zip',
  TEXT: 'file-text',
  OTHER: 'file',
};

@Component({
  selector: 'app-store-element',
  imports: [
    NzCardComponent,
    NzCardMetaComponent,
    NzButtonModule,
    NzIconModule,
    DecimalPipe,
  ],
  templateUrl: './store-element.html',
  styleUrl: './store-element.scss',
})
export class StoreElement implements OnChanges, OnDestroy {
  @Input({ required: true }) data!: FileNode;
  private storeService = inject(StoreService);
  private cdr = inject(ChangeDetectorRef);
  private sanitizer = inject(DomSanitizer);
  isDownloading = false;
  progress = 0;
  protected previewFailed = false;
  protected previewObjectUrl: string | null = null;
  public delete = output<string>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data']) {
      this.loadPreview();
    }
  }

  ngOnDestroy(): void {
    this.releasePreviewObjectUrl();
  }

  private loadPreview(): void {
    this.releasePreviewObjectUrl();
    this.previewFailed = false;

    if (this.previewKind === 'icon') {
      return;
    }

    this.storeService.getPreview(this.data.uuid).subscribe({
      next: (blob) => {
        this.previewObjectUrl = window.URL.createObjectURL(blob);
        this.cdr.detectChanges();
      },
      error: () => {
        this.previewFailed = true;
        this.cdr.detectChanges();
      },
    });
  }

  private releasePreviewObjectUrl(): void {
    if (this.previewObjectUrl) {
      window.URL.revokeObjectURL(this.previewObjectUrl);
      this.previewObjectUrl = null;
    }
  }

  open() {
    if (this.data.type === 'FOLDER') {
      this.storeService.changeParent(this.data.name, this.data.uuid);
    }
  }

  download() {
    this.isDownloading = true;
    this.progress = 0;

    this.storeService.downloadFile(this.data.uuid).subscribe({
      next: (event: HttpEvent<Blob>) => {
        if (event.type === HttpEventType.DownloadProgress) {
          if (event.total) {
            this.progress = Math.round((100 * event.loaded) / event.total);
            this.cdr.detectChanges();
          }
        }

        else if (event.type === HttpEventType.Response) {
          const blob = event.body;
          if (blob) {
            const blobUrl = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = blobUrl;
            anchor.download = this.data.name;
            document.body.appendChild(anchor);
            anchor.click();
            document.body.removeChild(anchor);
            window.URL.revokeObjectURL(blobUrl);
          }
          this.isDownloading = false;
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        console.error('Download error:', err);
        this.isDownloading = false;
        this.cdr.detectChanges();
      }
    });
  }

  get previewKind(): PreviewKind {
    return PREVIEW_KIND[this.data.type];
  }

  /**
   * `iframe[src]` is a RESOURCE_URL security context in Angular, so it must be explicitly
   * trusted rather than bound as a plain string. Safe here: the URL is always a blob: URL
   * created locally from the fetched preview, never from user-supplied input.
   */
  get trustedPreviewUrl(): SafeResourceUrl | null {
    return this.previewObjectUrl ? this.sanitizer.bypassSecurityTrustResourceUrl(this.previewObjectUrl) : null;
  }

  get fallbackIcon(): string {
    return TYPE_ICON[this.data.type];
  }

  onPreviewError(): void {
    this.previewFailed = true;
  }

  onDeleteClick(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();

    if (confirm(`Are you sure you want to delete "${this.data.name}"?`)) {
      this.delete.emit(this.data.uuid);
    }
  }
}
