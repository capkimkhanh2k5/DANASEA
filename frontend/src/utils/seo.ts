export const DEFAULT_SITE_ORIGIN = 'https://danasea.tailfc3563.ts.net';

/**
 * Resolves the canonical public origin dynamically from environment config or browser location,
 * falling back to the production Tailscale HTTPS domain.
 */
export function getSiteOrigin(): string {
  const envOrigin = import.meta.env.VITE_PUBLIC_ORIGIN;
  if (typeof envOrigin === 'string' && envOrigin.trim()) {
    return envOrigin.trim().replace(/\/+$/, '');
  }
  if (typeof window !== 'undefined' && window.location?.origin) {
    const origin = window.location.origin.replace(/\/+$/, '');
    if (!origin.includes('localhost') && !origin.includes('127.0.0.1')) {
      return origin;
    }
  }
  return DEFAULT_SITE_ORIGIN;
}

/**
 * Helper to construct clean, SEO-friendly tour URLs.
 * Prefers pure slug, falls back to ID if slug is not yet populated.
 */
export function getTourUrl(service: { id?: string; slug?: string } | string | null | undefined): string {
  if (!service) {
    return '/explore';
  }
  if (typeof service === 'string') {
    return `/tour/${encodeURIComponent(service)}`;
  }
  const identifier = service.slug || service.id;
  return identifier ? `/tour/${encodeURIComponent(identifier)}` : '/explore';
}
