import Foundation

public class AppGroupManager {
    public static let shared = AppGroupManager()
    private let appGroupID = "group.com.aieverywhere.app"

    private var defaults: UserDefaults? {
        return UserDefaults(suiteName: appGroupID)
    }

    public func saveStylePreference(formality: Int, verbosity: String, emojiPolicy: String) {
        defaults?.set(formality, forKey: "formality_score")
        defaults?.set(verbosity, forKey: "verbosity")
        defaults?.set(emojiPolicy, forKey: "emoji_policy")
        defaults?.synchronize()
    }

    public func loadStylePreference() -> (formality: Int, verbosity: String, emojiPolicy: String) {
        let formality = defaults?.integer(forKey: "formality_score") ?? 3
        let verbosity = defaults?.string(forKey: "verbosity") ?? "balanced"
        let emojiPolicy = defaults?.string(forKey: "emoji_policy") ?? "sparse"
        return (formality == 0 ? 3 : formality, verbosity, emojiPolicy)
    }
}
