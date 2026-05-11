import Foundation
import Hyperswitch
import UIKit

public final class CVCWidgetContainer: UIView {
    private(set) var widget: CVCWidget?

    @discardableResult
    func attach(
        paymentSession: PaymentSession,
        configuration: [String: Any] = [:],
        subscribe: ((PaymentEventSubscriptionBuilder) -> Void)? = nil
    ) -> CVCWidget {
        if let existing = widget { return existing }
        let widget = CVCWidget(configurationDict: configuration, subscribe: subscribe)
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
