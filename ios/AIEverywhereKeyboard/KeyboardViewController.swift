import UIKit

class KeyboardViewController: UIInputViewController, AiToolbarDelegate {
    @IBOutlet var nextKeyboardButton: UIButton!
    private var aiToolbarView: AiToolbarView!
    private var previewLabel: UILabel!
    private var insertButton: UIButton!
    private var previewContainer: UIView!
    
    private var pendingAiResult: String?

    override func updateViewConstraints() {
        super.updateViewConstraints()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }

    private func setupUI() {
        view.backgroundColor = UIColor(red: 0.09, green: 0.09, blue: 0.11, alpha: 1.0)

        // 1. Preview Container Card
        previewContainer = UIView()
        previewContainer.backgroundColor = UIColor(red: 0.12, green: 0.16, blue: 0.23, alpha: 1.0)
        previewContainer.isHidden = true
        previewContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(previewContainer)

        previewLabel = UILabel()
        previewLabel.textColor = UIColor(red: 0.20, green: 0.83, blue: 0.60, alpha: 1.0)
        previewLabel.font = UIFont.systemFont(ofSize: 13, weight: .regular)
        previewLabel.numberOfLines = 2
        previewLabel.translatesAutoresizingMaskIntoConstraints = false
        previewContainer.addSubview(previewLabel)

        insertButton = UIButton(type: .system)
        insertButton.setTitle("Insert", for: .normal)
        insertButton.backgroundColor = UIColor(red: 0.39, green: 0.40, blue: 0.95, alpha: 1.0)
        insertButton.setTitleColor(.white, for: .normal)
        insertButton.layer.cornerRadius = 12
        insertButton.contentEdgeInsets = UIEdgeInsets(top: 4, left: 10, bottom: 4, right: 10)
        insertButton.translatesAutoresizingMaskIntoConstraints = false
        insertButton.addTarget(self, action: #selector(didTapInsert), for: .touchUpInside)
        previewContainer.addSubview(insertButton)

        // 2. AI Toolbar
        aiToolbarView = AiToolbarView()
        aiToolbarView.delegate = self
        aiToolbarView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(aiToolbarView)

        // Layout Constraints
        NSLayoutConstraint.activate([
            previewContainer.topAnchor.constraint(equalTo: view.topAnchor),
            previewContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            previewContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            previewContainer.heightAnchor.constraint(equalToConstant: 48),

            previewLabel.leadingAnchor.constraint(equalTo: previewContainer.leadingAnchor, constant: 12),
            previewLabel.centerYAnchor.constraint(equalTo: previewContainer.centerYAnchor),
            previewLabel.trailingAnchor.constraint(equalTo: insertButton.leadingAnchor, constant: -8),

            insertButton.trailingAnchor.constraint(equalTo: previewContainer.trailingAnchor, constant: -12),
            insertButton.centerYAnchor.constraint(equalTo: previewContainer.centerYAnchor),

            aiToolbarView.topAnchor.constraint(equalTo: previewContainer.bottomAnchor),
            aiToolbarView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            aiToolbarView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            aiToolbarView.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    func didSelectAiAction(actionType: String, tone: String?) {
        guard let proxy = textDocumentProxy as? UITextDocumentProxy else { return }

        let selected = proxy.selectedText ?? ""
        let contextText = selected.isEmpty ? (proxy.documentContextBeforeInput ?? "") : selected

        if contextText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return
        }

        let sanitized = PiiSanitizer.sanitize(text: contextText)

        previewContainer.isHidden = false
        previewLabel.text = "[Processing AI request...]"
        insertButton.isEnabled = false

        ApiClient.shared.executeAction(actionType: actionType, inputText: sanitized, targetTone: tone) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let response):
                    self?.pendingAiResult = response.resultText
                    self?.previewLabel.text = "Preview: \(response.resultText)"
                    self?.insertButton.isEnabled = true
                case .failure:
                    self?.previewLabel.text = "[Network error or AI unavailable]"
                    self?.insertButton.isEnabled = false
                }
            }
        }
    }

    @objc private func didTapInsert() {
        guard let textToInsert = pendingAiResult,
              let proxy = textDocumentProxy as? UITextDocumentProxy else { return }

        proxy.insertText(textToInsert)
        previewContainer.isHidden = true
        pendingAiResult = nil
    }
}
