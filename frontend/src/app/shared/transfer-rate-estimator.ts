/**
 * Estimates remaining time for an in-progress transfer from its average throughput
 * so far (bytes moved / elapsed time). Instantiate one per transfer and call
 * `estimateSecondsRemaining` as new progress events arrive.
 */
export class TransferRateEstimator {
  private readonly startedAt = performance.now();

  estimateSecondsRemaining(loaded: number, total: number): number | null {
    if (loaded <= 0 || total <= 0 || loaded >= total) return null;

    const elapsedSeconds = (performance.now() - this.startedAt) / 1000;
    if (elapsedSeconds <= 0) return null;

    const bytesPerSecond = loaded / elapsedSeconds;
    if (bytesPerSecond <= 0) return null;

    return (total - loaded) / bytesPerSecond;
  }
}
