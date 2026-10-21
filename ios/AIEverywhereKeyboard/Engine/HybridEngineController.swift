import Foundation

public class HybridEngineController {
    public static let shared = HybridEngineController()

    public func executeLocalRuleEngine(actionType: String, text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch actionType {
        case "FIX_GRAMMAR":
            var fixed = trimmed.replacingOccurrences(of: "\\bi\\b", with: "I", options: .regularExpression)
            if !fixed.hasSuffix(".") { fixed += "." }
            return fixed
        case "REWRITE":
            return "Local Polished: \(trimmed)"
        case "SMART_REPLY":
            return "Got it, thank you!"
        default:
            return trimmed
        }
    }
}
