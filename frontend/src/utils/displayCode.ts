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
    clean.startsWith('00020000') ||
    clean.startsWith('00050000') ||
    clean.startsWith('00060000') ||
    clean.startsWith('00090000') ||
    clean.startsWith('b0010000') ||
    clean.startsWith('b0020000') ||
    clean.startsWith('00000000') ||
    new Set(clean.slice(0, 8).split('')).size <= 2;
  const code = isSeed
    ? (clean.slice(0, 4) + clean.slice(-4))
    : clean.substring(0, 8);
  return `${prefix}-${code.toUpperCase()}`;
}

/**
 * Checks whether user input (with or without # prefix) matches a target ID
 * either via raw UUID or via formatted display code.
 */
export function matchesDisplayCodeOrId(input: string, targetId: string, prefix = 'ORD'): boolean {
  if (!input || !targetId) return false;
  const cleanInput = input.trim().replace(/^#/, '').toUpperCase();
  const cleanTarget = targetId.trim().toUpperCase();
  if (cleanInput === cleanTarget) return true;
  if (cleanInput === formatDisplayCode(targetId, prefix).toUpperCase()) return true;
  if (formatDisplayCode(targetId, 'ORD').toUpperCase() === cleanInput) return true;
  if (formatDisplayCode(targetId, 'SUB').toUpperCase() === cleanInput) return true;
  return false;
}

const UUID_REGEX = /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g;

/**
 * Formats any raw 36-char UUIDs found in free text (like chat titles or messages)
 * into concise display codes (e.g. #ORD-00010042).
 */
export function formatTextWithDisplayCodes(text?: string | null): string {
  if (!text) return '';
  return text.replace(UUID_REGEX, (uuid) => {
    const clean = uuid.replace(/-/g, '').toLowerCase();
    let prefix = 'ORD';
    if (clean.startsWith('0002') || clean.startsWith('b002')) prefix = 'SUB';
    else if (clean.startsWith('0005')) prefix = 'DISP';
    else if (clean.startsWith('0006')) prefix = 'STL';
    else if (clean.startsWith('0009')) prefix = 'PAY';
    return `#${formatDisplayCode(uuid, prefix)}`;
  });
}

/**
 * Reconstructs a full 36-char UUID from a display code or short seed code.
 * (e.g. ORD-00010042 -> 00010000-0000-0000-0000-000000000042).
 * Returns original input if not matching the seed pattern.
 */
export function resolveDisplayCodeToUuid(input: string): string {
  if (!input) return '';
  const clean = input.trim().replace(/^#/, '');
  const match = clean.match(/^(?:ORD|SUB|DISP|PAY|STL|VND|DOC|LOG|CUST)-([0-9a-fA-F]{4})([0-9a-fA-F]{4})$/i);
  if (match) {
    const head = match[1].toLowerCase();
    const tail = match[2].toLowerCase();
    return `${head}0000-0000-0000-0000-00000000${tail}`;
  }
  return input;
}

/**
 * Translates any display codes in user messages into raw seed UUIDs
 * so that backend regexes (e.g. PolicyChatAnswer) and queries can locate the records.
 */
export function translateDisplayCodesToUuids(text?: string | null): string {
  if (!text) return '';
  return text.replace(/#?(?:ORD|SUB|DISP|PAY|STL|VND|DOC|LOG|CUST)-([0-9a-fA-F]{4})([0-9a-fA-F]{4})\b/gi, (_, head, tail) => {
    return `${head.toLowerCase()}0000-0000-0000-0000-00000000${tail.toLowerCase()}`;
  });
}

