import {ChangeDetectorRef, Component, inject, Input, OnChanges, OnDestroy, output, SimpleChanges} from '@angular/core';
import {FileNode, FileType, StoreService} from '../../../services/store';
import {NzCardComponent, NzCardMetaComponent} from 'ng-zorro-antd/card';
import {NzButtonModule} from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import {NzContextMenuService, NzDropdownMenuComponent, NzDropdownModule} from 'ng-zorro-antd/dropdown';
import {NzMenuModule} from 'ng-zorro-antd/menu';
import {formatBytes} from '../../../shared/format';
import {TransferService} from '../../../services/transfer';
import {TYPE_ICON} from '../../../shared/file-icons';
import {downloadFile} from '../../../shared/file-download';

type PreviewKind = 'image' | 'icon';

/**
 * Which widget renders a file's preview, keyed by its classified type. Video resolves
 * to 'image' too: the backend serves a generated thumbnail frame for video through the
 * same preview endpoint, so from here it's indistinguishable from a still image.
 * Adding preview support for a new type is a one-line change here.
 *
 * PDFs intentionally fall back to the icon: rendering a PDF in an <iframe> is not
 * accessible (no text alternative, unusable with a screen reader or keyboard-only, no
 * reflow), so no live preview is offered for that type.
 */
const PREVIEW_KIND: Record<FileType, PreviewKind> = {
  FOLDER: 'icon',
  IMAGE: 'image',
  VIDEO: 'image',
  AUDIO: 'icon',
  PDF: 'icon',
  DOCUMENT: 'icon',
  ARCHIVE: 'icon',
  TEXT: 'icon',
  OTHER: 'icon',
};

// TYPE_ICON (used as the 'icon' preview kind, and as the fallback when a live preview fails
// to load) lives in shared/file-icons.ts so the bin's list view can reuse the same mapping.

@Component({
  selector: 'app-store-element',
  imports: [
    NzCardComponent,
    NzCardMetaComponent,
    NzButtonModule,
    NzIconModule,
    NzDropdownModule,
    NzMenuModule,
  ],
  templateUrl: './store-element.html',
  styleUrl: './store-element.scss',
})
export class StoreElement implements OnChanges, OnDestroy {
  @Input({ required: true }) data!: FileNode;
  private storeService = inject(StoreService);
  private transferService = inject(TransferService);
  private cdr = inject(ChangeDetectorRef);
  private nzContextMenuService = inject(NzContextMenuService);
  isDownloading = false;
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

  onCardKeydown(event: KeyboardEvent): void {
    if (this.data.type !== 'FOLDER') return;

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.open();
    }
  }

  download() {
    this.isDownloading = true;

    downloadFile(this.storeService, this.transferService, this.data.uuid, this.data.name, () => {
      this.isDownloading = false;
      this.cdr.detectChanges();
    });
  }

  get previewKind(): PreviewKind {
    return PREVIEW_KIND[this.data.type];
  }

  get fallbackIcon(): string {
    return TYPE_ICON[this.data.type];
  }

  get displaySize(): string {
    const formatted = formatBytes(this.data.size);
    return this.data.type === 'FOLDER' ? `${formatted} (folder)` : formatted;
  }

  onPreviewError(): void {
    this.previewFailed = true;
  }

  onDeleteClick(): void {
    if (confirm(`Are you sure you want to move "${this.data.name}" to the bin?`)) {
      this.delete.emit(this.data.uuid);
    }
  }

  openContextMenu(event: MouseEvent, menu: NzDropdownMenuComponent): void {
    this.nzContextMenuService.create(event, menu);
  }
}
