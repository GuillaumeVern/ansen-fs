import { Component, Input } from '@angular/core';
import { formatBytes, formatDuration } from '../../../shared/format';

export interface BatchProgress {
  /** Count of files that have fully finished transferring. */
  filesDone: number;
  totalFiles: number;
  etaSeconds: number | null;
}

/** Displays transfer progress for a single file, optionally nested inside a multi-file batch. */
@Component({
  selector: 'app-transfer-progress',
  templateUrl: './transfer-progress.html',
  styleUrl: './transfer-progress.scss',
})
export class TransferProgress {
  @Input() title = '';
  @Input() fileName = '';
  @Input() loaded = 0;
  @Input() total = 0;
  @Input() etaSeconds: number | null = null;
  @Input() batch: BatchProgress | null = null;

  protected readonly formatBytes = formatBytes;
  protected readonly formatDuration = formatDuration;

  get filePercent(): number {
    return this.total > 0 ? Math.min(100, Math.round((100 * this.loaded) / this.total)) : 0;
  }

  get batchPercent(): number {
    if (!this.batch || this.batch.totalFiles <= 0) return 0;

    const currentFileFraction = this.total > 0 ? this.loaded / this.total : 0;
    return Math.min(100, Math.round(((this.batch.filesDone + currentFileFraction) / this.batch.totalFiles) * 100));
  }

  get currentFileNumber(): number {
    if (!this.batch) return 0;
    return Math.min(this.batch.filesDone + 1, this.batch.totalFiles);
  }
}
