/**
 * Display formatting helpers.
 *
 * Desert Order deals in tens of millions of resources, so raw numbers are unreadable.
 * Everything the player sees goes through here.
 */

/** 1250000 -> "1.25M". Keeps three significant figures. */
export function formatNumber(value: number): string {
  const n = Math.floor(value);
  const abs = Math.abs(n);
  if (abs >= 1_000_000_000) return `${trim(n / 1_000_000_000)}B`;
  if (abs >= 1_000_000) return `${trim(n / 1_000_000)}M`;
  if (abs >= 10_000) return `${trim(n / 1_000)}K`;
  return n.toLocaleString('en-US');
}

function trim(v: number): string {
  if (v >= 100) return v.toFixed(0);
  if (v >= 10) return v.toFixed(1).replace(/\.0$/, '');
  return v.toFixed(2).replace(/\.?0+$/, '');
}

/** Per-second rate, always signed. */
export function formatRate(value: number): string {
  const sign = value >= 0 ? '+' : '-';
  return `${sign}${formatNumber(Math.abs(value))}/s`;
}

/** 3725 -> "1h 02m". Used for build and research timers. */
export function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '--';
  const s = Math.ceil(seconds);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  const rs = s % 60;
  if (m < 60) return `${m}m ${String(rs).padStart(2, '0')}s`;
  const h = Math.floor(m / 60);
  return `${h}h ${String(m % 60).padStart(2, '0')}m`;
}

/** Elapsed match clock. */
export function formatClock(seconds: number): string {
  const s = Math.floor(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rs = s % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(rs).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function formatPercent(fraction: number): string {
  return `${Math.round(fraction * 100)}%`;
}

/** Escapes text destined for innerHTML. */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
