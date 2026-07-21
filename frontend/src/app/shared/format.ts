const SIZE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

/** Renders a byte count as a human-readable size, e.g. `1.5 MB` rather than `1572864 bytes`. */
export function formatBytes(bytes: number): string {
  if (!bytes) return '0 B';

  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), SIZE_UNITS.length - 1);
  const value = bytes / Math.pow(1024, exponent);

  return `${exponent === 0 ? value : value.toFixed(1)} ${SIZE_UNITS[exponent]}`;
}

/** Renders a duration in seconds as a short human-readable estimate, e.g. `2m 30s`. */
export function formatDuration(seconds: number): string {
  const wholeSeconds = Math.round(seconds);

  if (wholeSeconds < 1) return 'a few seconds';
  if (wholeSeconds < 60) return `${wholeSeconds}s`;

  const minutes = Math.floor(wholeSeconds / 60);
  const secs = wholeSeconds % 60;
  if (minutes < 60) return `${minutes}m ${secs}s`;

  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}h ${mins}m`;
}
