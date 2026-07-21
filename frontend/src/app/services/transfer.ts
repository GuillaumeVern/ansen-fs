import { Injectable, signal } from '@angular/core';

export interface DownloadTransfer {
  id: string;
  fileName: string;
  loaded: number;
  total: number;
  etaSeconds: number | null;
}

export interface UploadTransfer {
  currentFileName: string;
  currentFileLoaded: number;
  currentFileTotal: number;
  currentFileEtaSeconds: number | null;
  filesDone: number;
  totalFiles: number;
  overallEtaSeconds: number | null;
}

/**
 * Tracks in-flight uploads/downloads so their progress can be rendered in a single
 * global tray rather than inside each file card - the cards themselves may later be
 * displayed as table rows, which have no room for a detailed progress readout.
 */
@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly downloadsSignal = signal<DownloadTransfer[]>([]);
  readonly downloads = this.downloadsSignal.asReadonly();

  private readonly uploadSignal = signal<UploadTransfer | null>(null);
  readonly upload = this.uploadSignal.asReadonly();

  startDownload(id: string, fileName: string): void {
    this.downloadsSignal.update(list => [
      ...list.filter(d => d.id !== id),
      { id, fileName, loaded: 0, total: 0, etaSeconds: null },
    ]);
  }

  updateDownload(id: string, loaded: number, total: number, etaSeconds: number | null): void {
    this.downloadsSignal.update(list =>
      list.map(d => (d.id === id ? { ...d, loaded, total, etaSeconds } : d)),
    );
  }

  finishDownload(id: string): void {
    this.downloadsSignal.update(list => list.filter(d => d.id !== id));
  }

  startUpload(totalFiles: number): void {
    this.uploadSignal.set({
      currentFileName: '',
      currentFileLoaded: 0,
      currentFileTotal: 0,
      currentFileEtaSeconds: null,
      filesDone: 0,
      totalFiles,
      overallEtaSeconds: null,
    });
  }

  startUploadFile(currentFileName: string, currentFileTotal: number): void {
    this.uploadSignal.update(u => u && {
      ...u,
      currentFileName,
      currentFileLoaded: 0,
      currentFileTotal,
      currentFileEtaSeconds: null,
    });
  }

  updateUploadProgress(currentFileLoaded: number, currentFileEtaSeconds: number | null, overallEtaSeconds: number | null): void {
    this.uploadSignal.update(u => u && { ...u, currentFileLoaded, currentFileEtaSeconds, overallEtaSeconds });
  }

  completeUploadFile(): void {
    this.uploadSignal.update(u => u && { ...u, filesDone: u.filesDone + 1 });
  }

  finishUpload(): void {
    this.uploadSignal.set(null);
  }
}
