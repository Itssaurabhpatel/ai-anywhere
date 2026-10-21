import UIKit
import Social
import UniformTypeIdentifiers

class ShareViewController: UIViewController, ShareActionSheetDelegate {
    private var actionSheetView: ShareActionSheetView!
    private var extractedText: String = ""

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        extractSharedContent()
    }

    private func setupUI() {
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)

        actionSheetView = ShareActionSheetView()
        actionSheetView.delegate = self
        actionSheetView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(actionSheetView)

        NSLayoutConstraint.activate([
            actionSheetView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            actionSheetView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            actionSheetView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16)
        ])
    }

    private func extractSharedContent() {
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let itemProvider = extensionItem.attachments?.first else { return }

        if itemProvider.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
            itemProvider.loadItem(forTypeIdentifier: UTType.text.identifier, options: nil) { [weak self] (item, error) in
                if let text = item as? String {
                    DispatchQueue.main.async {
                        self?.extractedText = text
                        self?.actionSheetView.updateResultText("Extracted text: \"\(text.prefix(80))...\"\nTap an action below.")
                    }
                }
            }
        }
    }

    func didSelectAction(type: String) {
        actionSheetView.updateResultText("✨ Processing \(type)...")
        // Call Phase 2 backend endpoint (/v1/ai/context/summarize or /v1/ai/translate/multilingual)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            guard let self = self else { return }
            if type == "SUMMARIZE" {
                self.actionSheetView.updateResultText("• Main Point: \(self.extractedText.prefix(60))\n• Detail: Content extracted via Share Sheet.\n• Action: Ready to copy or paste.")
            } else if type == "EXPLAIN" {
                self.actionSheetView.updateResultText("Explanation: The selected text discusses main concepts from the active app screen.")
            } else {
                self.actionSheetView.updateResultText("Translation: Haan, \(self.extractedText.prefix(40)) - main isko bhej dunga.")
            }
        }
    }

    func didTapClose() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}
