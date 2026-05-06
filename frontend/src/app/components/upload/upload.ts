import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import {concatMap, from} from 'rxjs';

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

  onFolderSelected(event: any) {
    this.selectedFiles = Array.from(event.target.files);
  }

  uploadFolder(currentFolderUuid: string) {
    let formData: CreateJobRequest = {
      parentUuid: null,
      totalFiles: 0,
      manifest: [],
    };
    console.log(this.selectedFiles)

    formData.parentUuid = currentFolderUuid;
    let paths: string[] = [];

    this.selectedFiles.forEach((file: File) => {
      paths.push(file.webkitRelativePath);
    });
    formData.manifest = paths;
    formData.totalFiles = paths.length;

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
        formData.append('parentUuid', currentFolderUuid);

        chunk.forEach((file) => {
          console.log("uploading: " + file.webkitRelativePath);
        })
        return this.http.post(`api/files/jobs/${jobId}/upload`, formData);
      })
    ).subscribe({
      next: (response) => console.log('Chunk uploaded successfully:', response),
      error: (err) => console.error('An error occurred during the sequence:', err),
      complete: () => console.log('All chunks have been uploaded!')
    });
  }
}
