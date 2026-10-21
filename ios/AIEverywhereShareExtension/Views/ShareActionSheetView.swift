import UIKit

public protocol ShareActionSheetDelegate: AnyObject {
    func didSelectAction(type: String)
    func didTapClose()
}

public class ShareActionSheetView: UIView {
    public weak var delegate: ShareActionSheetDelegate?
    private let titleLabel = UILabel()
    private let resultTextView = UITextView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupUI()
    }

    private func setupUI() {
        backgroundColor = UIColor(red: 0.12, green: 0.16, blue: 0.23, alpha: 1.0)
        layer.cornerRadius = 16

        titleLabel.text = "AI Everywhere Assistant"
        titleLabel.textColor = .white
        titleLabel.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(titleLabel)

        resultTextView.backgroundColor = UIColor(red: 0.09, green: 0.09, blue: 0.11, alpha: 1.0)
        resultTextView.textColor = UIColor(red: 0.20, green: 0.83, blue: 0.60, alpha: 1.0)
        resultTextView.font = UIFont.systemFont(ofSize: 14)
        resultTextView.layer.cornerRadius = 8
        resultTextView.isEditable = false
        resultTextView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(resultTextView)

        let buttonStack = UIStackView()
        buttonStack.axis = .horizontal
        buttonStack.distribution = .fillEqually
        buttonStack.spacing = 8
        buttonStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(buttonStack)

        addActionButton(to: buttonStack, title: "Summarize", action: "SUMMARIZE")
        addActionButton(to: buttonStack, title: "Explain", action: "EXPLAIN")
        addActionButton(to: buttonStack, title: "Translate", action: "TRANSLATE")

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),

            resultTextView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            resultTextView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            resultTextView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            resultTextView.heightAnchor.constraint(equalToConstant: 120),

            buttonStack.topAnchor.constraint(equalTo: resultTextView.bottomAnchor, constant: 12),
            buttonStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            buttonStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            buttonStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16)
        ])
    }

    private func addActionButton(to stack: UIStackView, title: String, action: String) {
        let btn = UIButton(type: .system)
        btn.setTitle(title, for: .normal)
        btn.backgroundColor = UIColor(red: 0.39, green: 0.40, blue: 0.95, alpha: 1.0)
        btn.setTitleColor(.white, for: .normal)
        btn.layer.cornerRadius = 8
        btn.addAction(UIAction { [weak self] _ in
            self?.delegate?.didSelectAction(type: action)
        }, for: .touchUpInside)
        stack.addArrangedSubview(btn)
    }

    public func updateResultText(_ text: String) {
        resultTextView.text = text
    }
}
