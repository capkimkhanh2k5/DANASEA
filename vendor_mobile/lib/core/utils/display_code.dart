/// Standardized business code display formatter (synced with Web `displayCode.ts`).
/// Preserves raw UUIDs for APIs, backend queries, and navigation.
String formatDisplayCode(String id, {String prefix = 'ORD'}) {
  if (id.isEmpty) return '';
  final trimmed = id.trim();
  if (RegExp(r'^[A-Z]{3,6}-').hasMatch(trimmed)) {
    return trimmed;
  }
  final clean = trimmed.replaceAll('-', '');
  if (clean.isEmpty) return '';

  final isSeed = clean.startsWith('00010000') ||
      clean.startsWith('00020000') ||
      clean.startsWith('00040000') ||
      clean.startsWith('00050000') ||
      clean.startsWith('00060000') ||
      clean.startsWith('00080000') ||
      clean.startsWith('00090000') ||
      clean.startsWith('b0010000') ||
      clean.startsWith('b0020000') ||
      clean.startsWith('00000000') ||
      (clean.length >= 8 && clean.substring(0, 8).split('').toSet().length <= 2);

  final code = isSeed && clean.length >= 8
      ? (clean.substring(0, 4) + clean.substring(clean.length - 4))
      : (clean.length >= 8 ? clean.substring(0, 8) : clean);

  return '$prefix-${code.toUpperCase()}';
}

/// Helper that prepends '#' for UI tags: e.g. '#ORD-00010036', '#SUB-B0020040'
String formatDisplayCodeWithHash(String id, {String prefix = 'ORD'}) {
  final code = formatDisplayCode(id, prefix: prefix);
  if (code.isEmpty) return '';
  return code.startsWith('#') ? code : '#$code';
}
