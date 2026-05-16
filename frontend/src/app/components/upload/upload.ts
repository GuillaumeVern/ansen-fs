import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import {concatMap, from} from 'rxjs';
import {StoreService} from '../../services/store';

interface CreateJobRequest {
  parentUuid: string | null
  totalFiles: number
  manifest: string[]
}

@Component({
  selector: 'app-upload',
  imports: [],
  templateUrl: './upload.html',
  styleUrl: './upload.scss',
})
export class Upload {
  selectedFiles: File[] = [];

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

    const filePromises: Promise<File>[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.kind === 'file') {
        const entry = item.webkitGetAsEntry();
        if (entry) {
          this.traverseFileTree(entry, '', filePromises);
        }
      }
    }

    const resolvedFiles = await Promise.all(filePromises);
    this.selectedFiles = [...this.selectedFiles, ...resolvedFiles];
    console.log(`Parsed total of ${this.selectedFiles.length} files from drag-and-drop.`);
  }

  private traverseFileTree(entry: any, path: string, filePromises: Promise<File>[]) {
    if (entry.isFile) {
      filePromises.push(new Promise((resolve, reject) => {
        entry.file((file: File) => {
          const relativePath = path ? `${path}${entry.name}` : entry.name;
          Object.defineProperty(file, 'webkitRelativePath', {
            value: relativePath,
            writable: false
          });
          resolve(file);
        }, reject);
      }));
    } else if (entry.isDirectory) {
      const dirReader = entry.createReader();

      const readEntries = () => {
        dirReader.readEntries((entries: any[]) => {
          if (entries.length > 0) {
            for (const subEntry of entries) {
              this.traverseFileTree(subEntry, `${path}${entry.name}/`, filePromises);
            }
            readEntries();
          }
        });
      };
      readEntries();
    }
  }

  onItemsSelected(event: any) {
    this.selectedFiles = Array.from(event.target.files);
  }

  uploadItems(currentFolderUuid: string) {
    console.log("currentfolderuuid:", currentFolderUuid)
    if (this.selectedFiles.length === 0) return;

    let formData: CreateJobRequest = {
      parentUuid: currentFolderUuid,
      totalFiles: this.selectedFiles.length,
      manifest: [],
    };
    console.log(this.selectedFiles)

    let paths: string[] = [];

    this.selectedFiles.forEach((file: File) => {
      const relativePath = file.webkitRelativePath || file.name;
      paths.push(relativePath);
    });
    formData.manifest = paths;

    this.http.post('/api/files/jobs/new', formData).subscribe({
      next: (response: any) => {
        console.log('Upload started, Job ID:', response.jobId);
        this.uploadFiles(response.jobId, currentFolderUuid)
      },
      error: (err: any) => console.error('Upload failed', err)
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
      }
    });
  }
}
