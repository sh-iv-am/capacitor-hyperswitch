import Foundation
import Hyperswitch
import UIKit

public class HyperswitchImpl {

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static let shared = HyperswitchImpl()
    private init() {}

    // ── State ──────────────────────────────────────────────────────────────────

    private var paymentSession: PaymentSession?

    // Containers placed by the plugins in webView.scrollView. The SDK widget
    // is attached lazily during createElement(), once paymentSession exists.
    private weak var paymentElementContainer: PaymentElementContainer?
    private weak var cvcWidgetContainer: CVCWidgetContainer?

    /// Registry: handlerId → PaymentSessionHandler (supports multiple concurrent sessions)
    private var handlerRegistry: [String: PaymentSessionHandler] = [:]

    // ── Callback typealiases ───────────────────────────────────────────────────

    public typealias PaymentResultCallback = ([String: Any]) -> Void
    public typealias ErrorCallback = (String) -> Void
    public typealias VoidCallback = () -> Void
    public typealias HandlerReadyCallback = (String) -> Void

    // ── Container Registration ─────────────────────────────────────────────────
    func registerPaymentElementContainer(_ container: PaymentElementContainer?) {
        self.paymentElementContainer = container
    }

    func registerCVCWidgetContainer(_ container: CVCWidgetContainer?) {
        self.cvcWidgetContainer = container
    }

    private var paymentWidget: PaymentWidget? { paymentElementContainer?.widget }
    private var cvcWidget: CVCWidget? { cvcWidgetContainer?.widget }

    // ── Init ───────────────────────────────────────────────────────────────────

    func initialize(
        viewController: UIViewController,
        publishableKey: String,
        profileId: String?,
        customConfig: [String: Any]?,
        environment: String?
    ) {
        self.paymentSession = PaymentSession(
            publishableKey: publishableKey,
            profileId: profileId ?? "",
            customBackendUrl: customConfig?["overrideCustomBackendEndpoint"] as? String ?? nil,
            customLogUrl: customConfig?["overrideCustomLoggingEndpoint"] as? String ?? nil
        )
    }

    // ── Elements ───────────────────────────────────────────────────────────────

    /// Creates an Elements session and pre-fetches the PaymentSessionHandler.
    /// Calls onReady with a handlerId that the JS side holds onto.
    func elements(
        sdkAuthorization: String,
        onReady: @escaping HandlerReadyCallback,
        onError: @escaping ErrorCallback
    ) {
        DispatchQueue.main.async {
            guard let paymentSession = self.paymentSession else {
                onError("Hyperswitch not initialised — call init() first")
                return
            }
            paymentSession.initPaymentSession(sdkAuthorization: sdkAuthorization)
            paymentSession.getCustomerSavedPaymentMethods { handler in
                let handlerId = UUID().uuidString
                self.handlerRegistry[handlerId] = handler
                print(
                    "[Hyperswitch] Elements ready, PaymentSessionHandler stored with id: \(handlerId)"
                )
                onReady(handlerId)
            }

        }
    }

    // ── createElement ──────────────────────────────────────────────────────────

    /// Builds the SDK widget with session data and attaches it to the container
    /// the plugin already placed in the scrollView during create().
    func createElement(type: String, createOptions: [String: Any]?) {
        guard let paymentSession = paymentSession else {
            print("[Hyperswitch] elements() must be called before createElement()")
            return
        }

        DispatchQueue.main.async {
            let lower = type.lowercased()
            if lower == "paymentelement" || lower == "payment" {
                guard let container = self.paymentElementContainer else {
                    print("[Hyperswitch] PaymentElement container not registered — call create() first")
                    return
                }
                container.attach(
                    paymentSession: paymentSession,
                    configuration: ["merchantDisplayName": "ggggg"]
                )
                print("[Hyperswitch] PaymentElement created and placed successfully")

            } else if lower == "cvcwidget" || lower == "cvc" {
                guard let container = self.cvcWidgetContainer else {
                    print("[Hyperswitch] CVCWidget container not registered — call create() first")
                    return
                }
                container.attach(paymentSession: paymentSession, configuration: [:])
                print("[Hyperswitch] CVCWidget created and placed successfully")
            }
        }
    }

    // ── updateIntent ───────────────────────────────────────────────────────────

    func updateIntent(
        sdkAuthorization: String,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        //        guard let elements = elements else {
        //            onError("elements() must be called first")
        //            return
        //        }
        //
        //        let newConfig = PaymentSessionConfiguration(sdkAuthorization: sdkAuthorization)
        //
        //        elements.updateIntent({ _ in newConfig }) { result in
        //            print("[Hyperswitch] updateIntent result: \(result)")
        //            onResult(["type": String(describing: type(of: result))])
        //        }
    }

    // ── initPaymentSession (legacy) ────────────────────────────────────────────

    func initPaymentSession(
        sdkAuthorization: String,
        onReady: @escaping VoidCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let paymentSession = paymentSession else {
            onError("Hyperswitch not initialised — call init() first")
            return
        }
        paymentSession.initPaymentSession(sdkAuthorization: sdkAuthorization)
        onReady()
    }

    // ── presentPaymentSheet (legacy) ───────────────────────────────────────────

    func presentPaymentSheet(
        viewController: UIViewController,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let paymentSession = paymentSession else {
            onError("Payment session not initialised — call initPaymentSession first")
            return
        }

        DispatchQueue.main.async {
            paymentSession.presentPaymentSheetWithParams(viewController: viewController, params: [:]) {
                result in
                onResult(self.paymentResultToDict(result))
            }
        }
    }

    // ── confirmPayment ─────────────────────────────────────────────────────────

    func confirmPayment(
        confirmParams: [String: Any]?,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let paymentWidget = paymentWidget else {
            onError("PaymentElement not bound — call createElement() first")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            paymentWidget.confirm { result in
                onResult(self.paymentResultToDict(result))
            }
        }
    }

    // ── Flow 1: getCustomerSavedPaymentMethods (async) ─────────────────────────

    /// Fetches the PaymentSessionHandler from the active paymentSession,
    /// stores it in the registry, and returns the new handlerId.
    func getCustomerSavedPaymentMethods(
        onReady: @escaping HandlerReadyCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let session = paymentSession else {
            onError("paymentSession not ready — call initPaymentSession() first")
            return
        }
        DispatchQueue.main.async {
            session.getCustomerSavedPaymentMethods { [weak self] handler in
                guard let self = self else { return }
                let handlerId = UUID().uuidString
                self.handlerRegistry[handlerId] = handler
                print("[Hyperswitch] getCustomerSavedPaymentMethods ready, id: \(handlerId)")
                onReady(handlerId)
            }
        }
    }

    // ── Handler-scoped data accessors ──────────────────────────────────────────

    func getCustomerSavedPaymentMethodData(handlerId: String) -> [String: Any] {
        guard let handler = handlerRegistry[handlerId] else {
            print("[Hyperswitch] No handler for id: \(handlerId)")
            return [:]
        }
        let data = handler.getCustomerSavedPaymentMethodData()
        return data != nil ? ["data": String(describing: data)] : [:]
    }

    func getCustomerDefaultSavedPaymentMethodData(handlerId: String) -> [String: Any] {
        guard let handler = handlerRegistry[handlerId] else {
            print("[Hyperswitch] No handler for id: \(handlerId)")
            return [:]
        }
        let data = handler.getCustomerDefaultSavedPaymentMethodData()
        return data != nil ? ["data": String(describing: data)] : [:]
    }

    func getCustomerLastUsedPaymentMethodData(handlerId: String) -> [String: Any] {
        guard let handler = handlerRegistry[handlerId] else {
            print("[Hyperswitch] No handler for id: \(handlerId)")
            return [:]
        }
        let data = handler.getCustomerLastUsedPaymentMethodData()
        return data != nil ? ["data": String(describing: data)] : [:]
    }

    // ── Handler-scoped confirm methods ─────────────────────────────────────────

    func confirmWithCustomerLastUsedPaymentMethod(
        handlerId: String,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let handler = handlerRegistry[handlerId] else {
            onError("No handler for id: \(handlerId)")
            return
        }
        if let cvcWidget = cvcWidget {

            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                handler.confirmWithCustomerLastUsedPaymentMethod(cvcWidget) { result in
                    onResult(self.paymentResultToDict(result))
                }
            }
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private func paymentResultToDict(_ result: PaymentResult) -> [String: Any] {
        switch result {
        case .completed(let data):
            return ["type": "completed", "message": data ?? ""]
        case .canceled(let data):
            return ["type": "canceled", "message": data ?? ""]
        case .failed(let error):
            return ["type": "failed", "message": error.localizedDescription]
        @unknown default:
            return ["type": "failed", "message": "Unknown result"]
        }
    }
}
