import Foundation
import UIKit
import Hyperswitch

/// iOS counterpart to Android's `HyperswitchImpl.java`.
/// All method signatures and behaviour mirror the Android implementation.
public class HyperswitchImpl {

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static let shared = HyperswitchImpl()
    private init() {}

    // ── State ──────────────────────────────────────────────────────────────────

    private var hyperswitchInstance: HyperswitchInstance?

    // Elements API state
    private var elements: Elements?
    private var paymentSessionHandler: PaymentSessionHandler?
    private var paymentElementBound: HyperswitchBoundElement?
    private var cvcWidgetBound: HyperswitchBoundElement?

    // SDK view references (set by PaymentElementPlugin / CvcWidgetPlugin)
    private var paymentElementView: PaymentElement?
    private var cvcWidgetView: CVCWidget?

    // Legacy PaymentSession (used by presentPaymentSheet)
    private var paymentSession: PaymentSession?

    // ── Callback typealiases ───────────────────────────────────────────────────

    public typealias PaymentResultCallback = ([String: Any]) -> Void
    public typealias ErrorCallback = (String) -> Void
    public typealias VoidCallback = () -> Void

    // ── View Registration ──────────────────────────────────────────────────────

    func registerPaymentElementView(_ view: PaymentElement?) {
        self.paymentElementView = view
    }

    func registerCvcWidgetView(_ view: CVCWidget?) {
        self.cvcWidgetView = view
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    func initialize(
        viewController: UIViewController,
        publishableKey: String,
        profileId: String?,
        customConfig: [String: Any]?,
        environment: String?
    ) {
        var customEndpointConfig: CustomEndpointConfiguration? = nil
        if let config = customConfig {
            customEndpointConfig = CustomEndpointConfiguration(
                customEndpoint: config["customEndpoint"] as? String,
                overrideCustomBackendEndpoint: config["overrideCustomBackendEndpoint"] as? String,
                overrideCustomAssetsEndpoint: config["overrideCustomAssetsEndpoint"] as? String,
                overrideCustomSDKConfigEndpoint: config["overrideCustomSDKConfigEndpoint"] as? String,
                overrideCustomConfirmEndpoint: config["overrideCustomConfirmEndpoint"] as? String,
                overrideCustomAirborneEndpoint: config["overrideCustomAirborneEndpoint"] as? String,
                overrideCustomLoggingEndpoint: config["overrideCustomLoggingEndpoint"] as? String
            )
        }

        var env: HyperswitchEnvironment = .prod
        if environment == "SANDBOX" { env = .sandbox }
        else if environment == "INTEG" { env = .integ }

        print("[Hyperswitch] Initialized with publishableKey: \(publishableKey)")

        hyperswitchInstance = Hyperswitch.shared.initialize(
            viewController,
            config: HyperswitchConfiguration(
                publishableKey: publishableKey,
                profileId: profileId,
                customEndpointConfiguration: customEndpointConfig,
                environment: env
            )
        )
    }

    // ── Elements ───────────────────────────────────────────────────────────────

    /// Creates an Elements session and pre-fetches the PaymentSessionHandler.
    /// Mirrors: elements = hyperswitchInstance.elements(sessionConfig)
    func elements(
        sdkAuthorization: String,
        onReady: @escaping VoidCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let instance = hyperswitchInstance else {
            onError("Hyperswitch not initialised — call init() first")
            return
        }

        let sessionConfig = PaymentSessionConfiguration(sdkAuthorization: sdkAuthorization)

        instance.elements(sessionConfig) { [weak self] elementsInstance in
            guard let self = self else { return }
            self.elements = elementsInstance

            elementsInstance.getCustomerSavedPaymentMethods { handler in
                self.paymentSessionHandler = handler
                print("[Hyperswitch] Elements ready, PaymentSessionHandler loaded")
                onReady()
            }
        }
    }

    // ── createElement ──────────────────────────────────────────────────────────

    /// Binds a native view to the Elements session.
    /// Mirrors: paymentElementBound = elements.bind(paymentElement, buildConfiguration(), nil)
    func createElement(type: String, createOptions: [String: Any]?) {
        guard let elements = elements else {
            print("[Hyperswitch] elements() must be called before createElement()")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let lower = type.lowercased()

            if lower == "paymentelement" || lower == "payment" {
                guard let view = self.paymentElementView else {
                    print("[Hyperswitch] PaymentElementView not registered yet")
                    return
                }
                self.paymentElementBound = elements.bind(view, PaymentSheet.Configuration("abc"), nil)
                print("[Hyperswitch] PaymentElement bound successfully")

            } else if lower == "cvcwidget" || lower == "cvc" {
                guard let view = self.cvcWidgetView else {
                    print("[Hyperswitch] CvcWidgetView not registered yet")
                    return
                }
                self.cvcWidgetBound = elements.bind(view, nil, nil)
                print("[Hyperswitch] CVCWidget bound successfully")
            }
        }
    }

    // ── updateIntent ───────────────────────────────────────────────────────────

    func updateIntent(
        sdkAuthorization: String,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let elements = elements else {
            onError("elements() must be called first")
            return
        }

        let newConfig = PaymentSessionConfiguration(sdkAuthorization: sdkAuthorization)

        elements.updateIntent({ _ in newConfig }) { result in
            print("[Hyperswitch] updateIntent result: \(result)")
            onResult(["type": String(describing: type(of: result))])
        }
    }

    // ── initPaymentSession (legacy) ────────────────────────────────────────────

    func initPaymentSession(
        sdkAuthorization: String,
        onReady: @escaping VoidCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let instance = hyperswitchInstance else {
            onError("Hyperswitch not initialised — call init() first")
            return
        }

        instance.initPaymentSession(
            PaymentSessionConfiguration(sdkAuthorization: sdkAuthorization)
        ) { [weak self] session in
            self?.paymentSession = session
            print("[Hyperswitch] initPaymentSession ready")
            onReady()
        }
    }

    // ── presentPaymentSheet (legacy) ───────────────────────────────────────────

    func presentPaymentSheet(
        viewController: UIViewController,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let session = paymentSession else {
            onError("Payment session not initialised — call initPaymentSession first")
            return
        }

        DispatchQueue.main.async {
            session.launchPaymentSheet(PaymentSheet.Configuration("abc")) { [weak self] result in
                guard let self = self else { return }
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
        guard let bound = paymentElementBound else {
            onError("PaymentElement not bound — call createElement() first")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            bound.confirmPayment { result in
                onResult(self.paymentResultToDict(result))
            }
        }
    }

    // ── Saved payment methods ──────────────────────────────────────────────────

    func getCustomerSavedPaymentMethods() -> [String: Any] {
        guard let handler = paymentSessionHandler else {
            print("[Hyperswitch] paymentSessionHandler not ready")
            return [:]
        }
        let data = handler.getCustomerSavedPaymentMethodData()
        return data != nil ? ["data": String(describing: data!)] : [:]
    }

    func getCustomerDefaultSavedPaymentMethodData() -> [String: Any] {
        guard let handler = paymentSessionHandler else {
            print("[Hyperswitch] paymentSessionHandler not ready")
            return [:]
        }
        let data = handler.getCustomerDefaultSavedPaymentMethodData()
        return data != nil ? ["data": String(describing: data!)] : [:]
    }

    func getCustomerLastUsedPaymentMethodData() -> [String: Any] {
        guard let handler = paymentSessionHandler else {
            print("[Hyperswitch] paymentSessionHandler not ready")
            return [:]
        }
        let data = handler.getCustomerLastUsedPaymentMethodData()
        return data != nil ? ["data": String(describing: data!)] : [:]
    }

    // ── Confirm with saved methods ─────────────────────────────────────────────

    func confirmWithCustomerDefaultPaymentMethod(
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let handler = paymentSessionHandler else {
            onError("paymentSessionHandler not ready")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            handler.confirmWithCustomerDefaultPaymentMethod(self.cvcWidgetView) { result in
                onResult(self.paymentResultToDict(result))
            }
        }
    }

    func confirmWithCustomerLastUsedPaymentMethod(
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let handler = paymentSessionHandler else {
            onError("paymentSessionHandler not ready")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            handler.confirmWithCustomerLastUsedPaymentMethod(self.cvcWidgetView) { result in
                onResult(self.paymentResultToDict(result))
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
