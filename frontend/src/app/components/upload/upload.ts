import {HttpClient, HttpEvent, HttpEventType} from '@angular/common/http';
import {Component, ElementRef, inject, viewChild} from '@angular/core';
import {concatMap, from, tap} from 'rxjs';
import {StoreService} from '../../services/store';
import {TransferService} from '../../services/transfer';
import {NzButtonModule} from 'ng-zorro-antd/button';
import {NzIconModule} from 'ng-zorro-antd/icon';
import {TransferRateEstimator} from '../../shared/transfer-rate-estimator';

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
  private transferService = inject(TransferService);
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

  onEscapeKey(): void {
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
    const files = [...this.selectedFiles];

    this.transferService.startUpload(files.length);

    const overallTotalBytes = files.reduce((sum, file) => sum + file.size, 0);
    let bytesBeforeCurrentFile = 0;
    const overallEstimator = new TransferRateEstimator();

    from(files).pipe(
      concatMap((file) => {
        const fileName = file.webkitRelativePath || file.name;
        this.transferService.startUploadFile(fileName, file.size);
        const fileEstimator = new TransferRateEstimator();

        const formData = new FormData();
        formData.append('files', file);
        formData.append('parentUuid', currentFolderUuid || '');

        return this.http.post(`api/files/jobs/${jobId}/upload`, formData, {
          reportProgress: true,
          observe: 'events',
        }).pipe(
          tap((event: HttpEvent<any>) => {
            if (event.type === HttpEventType.UploadProgress) {
              const total = event.total ?? file.size;
              const etaSeconds = fileEstimator.estimateSecondsRemaining(event.loaded, total);
              const overallEtaSeconds = overallEstimator.estimateSecondsRemaining(
                bytesBeforeCurrentFile + event.loaded,
                overallTotalBytes,
              );
              this.transferService.updateUploadProgress(event.loaded, etaSeconds, overallEtaSeconds);
            } else if (event.type === HttpEventType.Response) {
              bytesBeforeCurrentFile += file.size;
              this.transferService.completeUploadFile();
            }
          })
        );
      })
    ).subscribe({
      error: (err) => {
        console.error('An error occurred during the sequence:', err);
        this.transferService.finishUpload();
      },
      complete: () => {
        this.selectedFiles = [];
        this.storeService.loadFiles(true);
        this.transferService.finishUpload();
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
