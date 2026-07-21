import { Component, inject } from '@angular/core';
import { BatchProgress, TransferProgress } from '../transfer-progress/transfer-progress';
import { TransferService, UploadTransfer } from '../../../services/transfer';

/** Global, fixed-position tray listing every in-flight upload/download, independent of any file list layout. */
@Component({
  selector: 'app-transfer-tray',
  imports: [TransferProgress],
  templateUrl: './transfer-tray.html',
  styleUrl: './transfer-tray.scss',
})
export class TransferTray {
  protected transferService = inject(TransferService);

  protected uploadBatch(upload: UploadTransfer): BatchProgress | null {
    return upload.totalFiles > 1
      ? { filesDone: upload.filesDone, totalFiles: upload.totalFiles, etaSeconds: upload.overallEtaSeconds }
      : null;
  }
}
