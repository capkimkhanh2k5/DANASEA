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
