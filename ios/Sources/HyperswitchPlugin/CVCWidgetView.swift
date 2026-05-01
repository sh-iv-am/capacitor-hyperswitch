import Foundation
import Hyperswitch
import UIKit

public final class CVCWidgetContainer: UIView {
    private(set) var widget: CVCWidget?

    @discardableResult
    func attach(paymentSession: PaymentSession, configuration: [String: Any] = [:]) -> CVCWidget {
        if let existing = widget { return existing }
        let widget = CVCWidget(configurationDict: configuration)
        widget.frame = bounds
//        widget.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        addSubview(widget)
        self.widget = widget
        return widget
    }

    func detach() {
        widget?.removeFromSuperview()
        widget = nil
    }
}
