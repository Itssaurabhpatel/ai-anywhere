import Foundation

public struct AiResponse {
    public let resultText: String
    public let providerUsed: String
    public let latencyMs: Int
}

public class ApiClient {
    public static let shared = ApiClient()
    private let endpoint = URL(string: "http://localhost:8080/v1/ai/execute")!

    public func executeAction(
        actionType: String,
        inputText: String,
        targetTone: String? = nil,
        completion: @escaping (Result<AiResponse, Error>) -> Void
    ) {
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("ios-keyboard-demo-01", forHTTPHeaderField: "x-device-id")
        request.timeoutInterval = 4.0 // Enforce strict 4s timeout for iOS keyboard extension

        var payload: [String: Any] = [
            "action_type": actionType,
            "input_text": inputText
        ]
        if let tone = targetTone {
            payload["target_tone"] = tone
        }

        guard let bodyData = try? JSONSerialization.data(withJSONObject: payload) else {
            completion(.failure(NSError(domain: "InvalidPayload", code: 400)))
            return
        }

        request.httpBody = bodyData

        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }

            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let resultText = json["result_text"] as? String else {
                completion(.failure(NSError(domain: "InvalidResponse", code: 500)))
                return
            }

            let provider = json["provider_used"] as? String ?? "ai-orchestrator"
            let latency = json["execution_latency_ms"] as? Int ?? 0

            completion(.success(AiResponse(resultText: resultText, providerUsed: provider, latencyMs: latency)))
        }

        task.resume()
    }
}
