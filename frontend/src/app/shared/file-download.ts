import { HttpEvent, HttpEventType } from '@angular/common/http';
import { StoreService } from '../services/store';
import { TransferService } from '../services/transfer';
import { TransferRateEstimator } from './transfer-rate-estimator';

/**
 * Downloads a file through the browser, tracking progress in the global transfer tray.
 * Shared by the grid view (store-element) and the list view (store), which both trigger
 * downloads but render progress nowhere themselves.
 */
export function downloadFile(
  storeService: StoreService,
  transferService: TransferService,
  uuid: string,
  name: string,
  onSettled: () => void,
): void {
  const estimator = new TransferRateEstimator();
  transferService.startDownload(uuid, name);

  storeService.downloadFile(uuid).subscribe({
    next: (event: HttpEvent<Blob>) => {
      if (event.type === HttpEventType.DownloadProgress) {
        const etaSeconds = event.total ? estimator.estimateSecondsRemaining(event.loaded, event.total) : null;
        transferService.updateDownload(uuid, event.loaded, event.total ?? 0, etaSeconds);
      } else if (event.type === HttpEventType.Response) {
        const blob = event.body;
        if (blob) {
          const blobUrl = window.URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = blobUrl;
          anchor.download = name;
          document.body.appendChild(anchor);
          anchor.click();
          document.body.removeChild(anchor);
          window.URL.revokeObjectURL(blobUrl);
        }
        transferService.finishDownload(uuid);
        onSettled();
      }
    },
    error: (err) => {
      console.error('Download error:', err);
      transferService.finishDownload(uuid);
      onSettled();
    },
  });
}
