import {ChangeDetectorRef, Component, inject, Input, output} from '@angular/core';
import {StoreService} from '../../../services/store';
import {FileNode} from '../store';
import {NzCardComponent, NzCardMetaComponent} from 'ng-zorro-antd/card';
import {NzButtonModule} from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import {HttpEvent, HttpEventType} from '@angular/common/http';
import {DecimalPipe} from '@angular/common';

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
export class StoreElement {
  @Input({ required: true }) data!: FileNode;
  private storeService = inject(StoreService);
  private cdr = inject(ChangeDetectorRef);
  isDownloading = false;
  progress = 0;
  public delete = output<string>();

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

  isSupportedImage(fileName: string): boolean {
    if (!fileName) return false;
    const extension = fileName.split('.').pop()?.toLowerCase();
    return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(extension || '');
  }

  onDeleteClick(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();

    if (confirm(`Are you sure you want to delete "${this.data.name}"?`)) {
      this.delete.emit(this.data.uuid);
    }
  }
}
