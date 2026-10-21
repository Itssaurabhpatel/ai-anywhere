import Foundation

public class PiiSanitizer {
    public static func sanitize(text: String) -> String {
        var sanitized = text
        
        // Redact Credit Cards
        sanitized = sanitized.replacingOccurrences(
            of: "\\b(?:\\d[ -]*?){13,16}\\b",
            with: "[CARD_REDACTED]",
            options: .regularExpression
        )
        
        // Redact Phone Numbers
        sanitized = sanitized.replacingOccurrences(
            of: "(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}",
            with: "[PHONE_REDACTED]",
            options: .regularExpression
        )
        
        // Redact Emails
        sanitized = sanitized.replacingOccurrences(
            of: "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            with: "[EMAIL_REDACTED]",
            options: .regularExpression
        )
        
        return sanitized
    }
}
