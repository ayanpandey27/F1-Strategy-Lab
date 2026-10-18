/** Lap time as m:ss.SSS (or ss.SSS under a minute). */
export function lapTime(ms: number | null | undefined): string {
  if (ms == null) return '–';
  const minutes = Math.floor(ms / 60000);
  const seconds = (ms % 60000) / 1000;
  return minutes > 0 ? `${minutes}:${seconds.toFixed(3).padStart(6, '0')}` : seconds.toFixed(3);
}

/** Plain seconds, e.g. 22.345s. */
export function seconds(ms: number | null | undefined, digits = 3): string {
  return ms == null ? '–' : `${(ms / 1000).toFixed(digits)}s`;
}

/** Signed seconds, e.g. +1.234s / −0.500s. */
export function signedSeconds(ms: number | null | undefined, digits = 3): string {
  if (ms == null) return '–';
  const s = (ms / 1000).toFixed(digits);
  return ms > 0 ? `+${s}s` : ms < 0 ? `−${s.slice(1)}s` : `${s}s`;
}
