import UIKit

public protocol AiToolbarDelegate: AnyObject {
    func didSelectAiAction(actionType: String, tone: String?)
}

public class AiToolbarView: UIView {
    public weak var delegate: AiToolbarDelegate?
    private let stackView = UIStackView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }

    private func setupView() {
        backgroundColor = UIColor(red: 0.12, green: 0.16, blue: 0.23, alpha: 1.0)
        
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.showsHorizontalScrollIndicator = false
        addSubview(scrollView)

        stackView.axis = .horizontal
        stackView.spacing = 6
        stackView.alignment = .center
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),

            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.heightAnchor)
        ])

        addActionButton(title: "AI", action: "FIX_GRAMMAR", isPrimary: true)
        addActionButton(title: "Fix", action: "FIX_GRAMMAR")
        addActionButton(title: "Rewrite", action: "REWRITE")
        addActionButton(title: "Reply", action: "SMART_REPLY")
        addActionButton(title: "Translate", action: "TRANSLATE")
        addActionButton(title: "Tone", action: "TONE_CHANGE", tone: "PROFESSIONAL")
    }

    private func addActionButton(title: String, action: String, tone: String? = nil, isPrimary: Bool = false) {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 13, weight: .medium)
        button.contentEdgeInsets = UIEdgeInsets(top: 6, left: 12, bottom: 6, right: 12)
        button.layer.cornerRadius = 14
        
        if isPrimary {
            button.backgroundColor = UIColor(red: 0.39, green: 0.40, blue: 0.95, alpha: 1.0)
            button.setTitleColor(.white, for: .normal)
        } else {
            button.backgroundColor = UIColor(red: 0.15, green: 0.15, blue: 0.16, alpha: 1.0)
            button.setTitleColor(UIColor(red: 0.97, green: 0.98, blue: 0.99, alpha: 1.0), for: .normal)
        }

        button.addAction(UIAction { [weak self] _ in
            self?.delegate?.didSelectAiAction(actionType: action, tone: tone)
        }, for: .touchUpInside)

        stackView.addArrangedSubview(button)
    }
}
