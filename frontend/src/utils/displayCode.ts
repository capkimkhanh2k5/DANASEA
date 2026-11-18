/**
 * DANASEA — Display Code Formatter
 * 
 * Standardizes display codes for Admin Frontend while preserving raw UUIDs
 * for API requests, routing, database entities, and clipboard copies.
 * 
 * Precedence / Formatting Rules:
 * - Master Order: ORD-XXXXXXXX
 * - Sub Order: SUB-XXXXXXXX
 * - Dispute: DISP-XXXXXXXX
 * - Payout: PAY-XXXXXXXX
 * - Settlement: STL-XXXXXXXX
 * - Customer: CUST-XXXXXXXX
 * - Audit Log: LOG-XXXXXXXX
 * - Service Slot: SLOT-XXXXXXXX
 * - Document: DOC-XXXXXXXX
 * - Vendor: VND-XXXXXXXX
 */
export function formatDisplayCode(id: string, prefix = 'ORD'): string {
  if (!id) return '';
  // If id is already formatted with a standard uppercase prefix (e.g. ORD-..., DISP-...)
  if (/^[A-Z]{3,6}-/.test(id)) {
    return id;
  }
  const clean = id.replace(/-/g, '');
  const isSeed =
    clean.startsWith('00010000') ||
    clean.startsWith('00060000') ||
    clean.startsWith('00000000') ||
    new Set(clean.slice(0, 8).split('')).size <= 2;
  const code = isSeed
    ? (clean.slice(0, 4) + clean.slice(-4))
    : clean.substring(0, 8);
  return `${prefix}-${code.toUpperCase()}`;
}
