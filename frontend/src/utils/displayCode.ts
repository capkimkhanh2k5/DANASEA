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

function inferPrefixFromContext(text: string, offset: number): string {
  const before = text.slice(Math.max(0, offset - 48), offset).toLowerCase();
  if (/(?:mã\s*ca|ca\s*này|ca\s*khởi\s*hành|\bslot\b)/i.test(before)) return 'SLOT';
  if (/(?:đơn\s*phụ|đơn\s*con|sub[- ]?order)/i.test(before)) return 'SUB';
  if (/(?:khiếu\s*nại|tranh\s*chấp|\bdispute\b)/i.test(before)) return 'DISP';
  if (/(?:dịch\s*vụ|\bservice\b)/i.test(before)) return 'SRV';
  if (/(?:đánh\s*giá|\breview\b)/i.test(before)) return 'REV';
  if (/(?:hoàn\s*tiền|\brefund\b)/i.test(before)) return 'REF';
  if (/(?:thiết\s*bị|\bequipment\b)/i.test(before)) return 'EQP';
  if (/(?:rút\s*tiền|chi\s*trả|\bpayout\b)/i.test(before)) return 'PAY';
  if (/(?:đối\s*tác|nhà\s*cung\s*cấp|\bvendor\b)/i.test(before)) return 'VND';
  return 'ORD';
}

/**
 * Formats any raw 36-char UUIDs found in free text (like chat titles or messages)
 * into concise display codes (e.g. #ORD-00010042, #SLOT-85E6BFFC).
 */
export function formatTextWithDisplayCodes(text?: string | null): string {
  if (!text) return '';
  return text.replace(UUID_REGEX, (uuid, offset: number) => {
    const clean = uuid.replace(/-/g, '').toLowerCase();
    let prefix: string;
    if (clean.startsWith('0001') || clean.startsWith('b001')) prefix = 'ORD';
    else if (clean.startsWith('0002') || clean.startsWith('b002')) prefix = 'SUB';
    else if (clean.startsWith('0004')) prefix = 'REF';
    else if (clean.startsWith('0005') || clean.startsWith('77777777')) prefix = 'DISP';
    else if (clean.startsWith('0006')) prefix = 'STL';
    else if (clean.startsWith('0008') || clean.startsWith('0009')) prefix = 'PAY';
    else if (clean.startsWith('66666666')) prefix = 'REV';
    else prefix = inferPrefixFromContext(text, offset);
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

