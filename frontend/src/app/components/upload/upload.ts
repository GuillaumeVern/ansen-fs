import { HttpClient } from '@angular/common/http';
import {Component, ElementRef, inject, viewChild} from '@angular/core';
import {concatMap, from} from 'rxjs';
import {StoreService} from '../../services/store';
import {NzButtonModule} from 'ng-zorro-antd/button';
import {NzIconModule} from 'ng-zorro-antd/icon';

interface CreateJobRequest {
  parentUuid: string | null
  totalFiles: number
  manifest: string[]
}

@Component({
  selector: 'app-upload',
  imports: [
    NzButtonModule,
    NzIconModule,
  ],
  templateUrl: './upload.html',
  styleUrl: './upload.scss',
})
export class Upload {
  selectedFiles: File[] = [];

  private fileInputRef = viewChild<ElementRef<HTMLInputElement>>('fileInput');
  private folderInputRef = viewChild<ElementRef<HTMLInputElement>>('folderInput');

  private http = inject(HttpClient);
  protected storeService = inject(StoreService);
  protected isDraggingOver = false;

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingOver = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingOver = false;
  }

  async onFileDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingOver = false;

    const items = event.dataTransfer?.items;
    if (!items) return;

    const filePromises: Promise<File[]>[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.kind === 'file') {
        const entry = item.webkitGetAsEntry();
        if (entry) {
          filePromises.push(this.traverseFileTree(entry, ''));
        }
      }
    }

    try {
      const resolvedGroups = await Promise.all(filePromises);

      const flattenedFiles = resolvedGroups.flat();

      this.selectedFiles = [...this.selectedFiles, ...flattenedFiles];

      if (this.selectedFiles.length > 0) {
        this.uploadItems();
      }
    } catch (err) {
      console.error("Failed parsing dragged items:", err);
    }
  }

  cancelDragOverlay(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();

    this.isDraggingOver = false;
  }

  private traverseFileTree(entry: any, path: string): Promise<File[]> {
    return new Promise((resolve, reject) => {
      if (entry.isFile) {
        entry.file((file: File) => {
          const relativePath = path ? `${path}${entry.name}` : entry.name;
          Object.defineProperty(file, 'webkitRelativePath', {
            value: relativePath,
            writable: false
          });
          resolve([file]);
        }, reject);
      } else if (entry.isDirectory) {
        const dirReader = entry.createReader();
        const allFiles: File[] = [];

        const readEntries = () => {
          dirReader.readEntries(async (entries: any[]) => {
            if (entries.length === 0) {
              resolve(allFiles);
            } else {
              const subPromises = entries.map(subEntry =>
                this.traverseFileTree(subEntry, `${path}${entry.name}/`)
              );

              const subFilesGroups = await Promise.all(subPromises);
              allFiles.push(...subFilesGroups.flat());

              readEntries();
            }
          }, reject);
        };

        readEntries();
      } else {
        resolve([]);
      }
    });
  }

  onItemsSelected(event: any) {
    this.selectedFiles = Array.from(event.target.files);
  }

  uploadItems() {
    if (this.selectedFiles.length === 0) return;

    const currentFolderUuid = this.storeService.currentParentUuid();

    let formData: CreateJobRequest = {
      parentUuid: currentFolderUuid,
      totalFiles: this.selectedFiles.length,
      manifest: [],
    };

    let paths: string[] = [];
    this.selectedFiles.forEach((file: File) => {
      const relativePath = file.webkitRelativePath || file.name;
      paths.push(relativePath);
    });
    formData.manifest = paths;

    this.http.post('/api/files/jobs/new', formData).subscribe({
      next: (response: any) => {
        console.log('Upload job generated:', response.jobId);
        this.uploadFiles(response.jobId, currentFolderUuid);
      },
      error: (err: any) => console.error('Upload allocation failure:', err)
    });
  }

  private uploadFiles(jobId: string, currentFolderUuid: string) {
    let chunkSize = 3;
    let allFiles = [...this.selectedFiles];
    let chunks: File[][] = [];

    while (allFiles.length > 0) {
      chunks.push(allFiles.splice(0, chunkSize));
    }

    from(chunks).pipe(
      concatMap((chunk) => {
        const formData = new FormData();
        chunk.forEach(file => formData.append('files', file));
        formData.append('parentUuid', currentFolderUuid || '');

        chunk.forEach((file) => {
          console.log("uploading: " + file.webkitRelativePath);
        })
        return this.http.post(`api/files/jobs/${jobId}/upload`, formData);
      })
    ).subscribe({
      next: (response) => console.log('Chunk uploaded successfully:', response),
      error: (err) => console.error('An error occurred during the sequence:', err),
      complete: () => {
        console.log('All chunks have been uploaded!')
        this.selectedFiles = [];
        this.storeService.loadFiles(true);
      }
    });
  }

  public triggerFileInput(): void {
    this.fileInputRef()?.nativeElement.click();
  }

  public triggerFolderInput(): void {
    this.folderInputRef()?.nativeElement.click();
  }
}
