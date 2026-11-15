/**
 * Smart Language Detector for AI Copilot Interactions
 * Detects whether the user prompt is written in Vietnamese or English.
 * 
 * Rules:
 * 1. If text contains Vietnamese diacritics -> 'vi'
 * 2. If text contains common unaccented Vietnamese words -> 'vi'
 * 3. If text contains English keywords/grammar words without Vietnamese markers -> 'en'
 * 4. Fallback default for local Da Nang operational context -> 'vi'
 */
export function detectLanguage(text?: string | null): 'vi' | 'en' {
  if (!text || !text.trim()) return 'vi';
  const trimmed = text.trim();

  // 1. Vietnamese diacritics check (100% definitive)
  const VIETNAMESE_DIACRITICS = /[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i;
  if (VIETNAMESE_DIACRITICS.test(trimmed)) {
    return 'vi';
  }

  // 2. Common Vietnamese unaccented words (informal/telex typing)
  const VIETNAMESE_UNACCENTED_WORDS = /\b(chao|toi|ban|hom nay|doanh thu|don hang|dich vu|khach|ve|ca|gio|tuan|thang|nam|bao cao|xem|co|khong|duoc|sao|the nao|nhu the nao|giup|gui|ho tro|gian hang|ben bai|tau|cano)\b/i;
  if (VIETNAMESE_UNACCENTED_WORDS.test(trimmed)) {
    return 'vi';
  }

  // 3. English keywords (greetings, question words, business terms)
  const ENGLISH_WORDS = /\b(hi|hello|hey|good|morning|afternoon|evening|what|how|why|when|where|who|which|is|are|am|was|were|the|a|an|in|on|at|to|for|of|with|from|by|about|show|tell|give|my|our|your|their|revenue|today|yesterday|tomorrow|week|month|year|sales|booking|bookings|order|orders|service|services|listing|listings|slot|slots|schedule|cancel|cancellation|refund|refunds|help|please|thank|thanks|yes|no)\b/i;
  if (ENGLISH_WORDS.test(trimmed)) {
    return 'en';
  }

  return 'vi';
}
