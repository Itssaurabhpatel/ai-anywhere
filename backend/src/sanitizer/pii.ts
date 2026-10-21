/**
 * Server-side PII Scrubbing and Content Sanitizer
 * Redacts credit cards, phone numbers, emails, and govt IDs before passing to AI providers.
 */
export class PiiSanitizer {
  private static CREDIT_CARD_REGEX = /\b(?:\d[ -]*?){13,16}\b/g;
  private static PHONE_REGEX = /(\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b/g;
  private static EMAIL_REGEX = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;
  private static SSN_AADHAAR_REGEX = /\b\d{4}[-\s]?\d{4}[-\s]?\d{4}\b/g;
  private static OTP_PIN_REGEX = /\b(?:otp|pin|code)\s*[:=]?\s*\d{4,8}\b/gi;

  public static sanitize(text: string): { sanitizedText: string; redactedCount: number } {
    let count = 0;
    let result = text;

    result = result.replace(this.CREDIT_CARD_REGEX, () => { count++; return '[CARD_REDACTED]'; });
    result = result.replace(this.PHONE_REGEX, () => { count++; return '[PHONE_REDACTED]'; });
    result = result.replace(this.EMAIL_REGEX, () => { count++; return '[EMAIL_REDACTED]'; });
    result = result.replace(this.SSN_AADHAAR_REGEX, () => { count++; return '[GOVT_ID_REDACTED]'; });
    result = result.replace(this.OTP_PIN_REGEX, () => { count++; return '[SECRET_REDACTED]'; });

    return { sanitizedText: result, redactedCount: count };
  }

  public static isSensitiveField(text: string): boolean {
    // Check if input consists solely of digits/OTP or password hints
    if (/^\d{4,8}$/.test(text.trim())) return true;
    if (/password|cvv|expiry|ssn|aadhaar|pin/i.test(text)) return true;
    return false;
  }
}
