import Foundation
import Hyperswitch
import UIKit

public final class PaymentElementContainer: UIView {
    private(set) var widget: PaymentWidget?

    @discardableResult
    func attach(
        paymentSession: PaymentSession,
        configuration: [String: Any],
        subscribe: ((PaymentEventSubscriptionBuilder) -> Void)? = nil
    ) -> PaymentWidget {
        if let existing = widget { return existing }
        let widget = PaymentWidget(
            paymentSession: paymentSession,
            configurationDict: configuration,
            subscribe: subscribe
        )
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
