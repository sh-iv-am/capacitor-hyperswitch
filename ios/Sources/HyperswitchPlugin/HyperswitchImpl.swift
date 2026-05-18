import Foundation
import Hyperswitch
import UIKit

public class HyperswitchImpl {

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static let shared = HyperswitchImpl()
    private init() {}

    // ── State ──────────────────────────────────────────────────────────────────

    private var hyperswitch: Hyperswitch?
    private var paymentSession: PaymentSession?
    private var currentSdkAuthorization: String?

    private var paymentElementConfirm: PaymentResultCallback?

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

    /// Fires when a native widget emits an event (e.g. CVC_STATUS, FORM_STATUS).
    public typealias NativeEventListener = (_ type: String, _ payload: [String: Any], _ source: String) -> Void

    // ── Event forwarding ───────────────────────────────────────────────────────

    private var eventListener: NativeEventListener?

    /// Called by HyperswitchPlugin.load() to receive widget events for notifyListeners.
    func setEventListener(_ listener: @escaping NativeEventListener) {
        self.eventListener = listener
    }

    private func fireEvent(type: String, payload: [String: Any]?, source: String) {
        eventListener?(type, payload ?? [:], source)
    }

    // ── Container Registration ─────────────────────────────────────────────────
    func registerPaymentElementContainer(_ container: PaymentElementContainer?) {
        self.paymentElementContainer = container
    }

    func registerCVCWidgetContainer(_ container: CVCWidgetContainer?) {
        self.cvcWidgetContainer = container
    }

    private var paymentWidget: PaymentWidget? { paymentElementContainer?.widget }
    private var cvcWidget: CVCWidget? { cvcWidgetContainer?.widget }
    private var onPaymentConfirmCallback: ((Bool) -> Void)? = nil
    // ── Init ───────────────────────────────────────────────────────────────────

    func initialize(
        viewController: UIViewController,
        publishableKey: String,
        profileId: String?,
        customConfig: [String: Any]?,
        environment: String?
    ) {
        var customEndpointConfiguration: CustomEndpointConfiguration?
        var environmentConfig: HyperswitchEnvironment?

        if let customEndpointConfig = customConfig?["CustomEndpointConfiguration"] as? [String: Any],
            let config = customEndpointConfig["customEndpointConfig"] as? String
        {
            customEndpointConfiguration = CustomEndpointConfiguration.commonEndpoint(config)
        }

        if let overrideEndpontConfiguration = customConfig?["OverrideEndpontConfiguration"] as? [String: Any] {
            let config = OverrideEndpointConfiguration(
                customBackendEndpoint: overrideEndpontConfiguration["customBackendEndpoint"] as? String,
                customAssetEndpoint: overrideEndpontConfiguration["customAssetEndpoint"] as? String,
                customSDKConfigEndpoint: overrideEndpontConfiguration["customSDKConfigEndpoint"] as? String,
                customAirborneEndpoint: overrideEndpontConfiguration["customAirborneEndpoint"] as? String,
                customLoggingEndpoint: overrideEndpontConfiguration["customLoggingEndpoint"] as? String
            )
            customEndpointConfiguration = CustomEndpointConfiguration.overrideEndpoints(config)
        }

        if let environment = environment {
            environmentConfig = HyperswitchEnvironment(rawValue: environment.lowercased())
        }

        let hyperswitchConfiguration = HyperswitchConfiguration(
            publishableKey: publishableKey,
            profileId: profileId,
            customEndpoints: customEndpointConfiguration,
            environment: environmentConfig
        )
        self.hyperswitch = Hyperswitch(configuration: hyperswitchConfiguration)
    }

    // ── Elements ───────────────────────────────────────────────────────────────

    /// Creates an Elements session and pre-fetches the PaymentSessionHandler.
    /// Calls onReady with a handlerId that the JS side holds onto.
    func elements(
        sdkAuthorization: String,
        onReady: @escaping HandlerReadyCallback,
        onError: @escaping ErrorCallback
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let hyperswitch = self?.hyperswitch else {
                onError("Hyperswitch not initialised — call init() first")
                return
            }
            let paymentSessionConfiguration = PaymentSessionConfiguration(sdkAuthorization: sdkAuthorization)
            self?.paymentSession = hyperswitch.initPaymentSession(configuration: paymentSessionConfiguration)
            self?.paymentSession?.getCustomerSavedPaymentMethods { handler in
                let handlerId = UUID().uuidString
                self?.handlerRegistry[handlerId] = handler
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
        guard let paymentSession = paymentSession,
            let hyperswitch = hyperswitch
        else {
            print("[Hyperswitch] elements() must be called before createElement()")
            return
        }

        DispatchQueue.main.async {
            let lower = type.lowercased()
            var configMap = createOptions ?? [:]
            let subscribedEvents = Self.extractAndRemoveSubscribedEvents(&configMap)

            if lower == "paymentelement" || lower == "payment" {
                guard let container = self.paymentElementContainer else {
                    print("[Hyperswitch] PaymentElement container not registered — call create() first")
                    return
                }
                let paymentElementref = container.attach(
                    paymentSession: paymentSession,
                    configuration: configMap,
                    completion: { [weak self] completion in
                        guard let self = self else { return }
                        self.paymentElementConfirm?(self.paymentResultToDict(completion))
                        self.fireEvent(
                            type: "onPaymentResultEvent",
                            payload: self.paymentResultToDict(completion),
                            source: "onPaymentResultEvent"
                        )
                    },
                    subscribe: { [weak self] builder in
                        self?.bindPaymentElementEvents(builder: builder, subscribed: subscribedEvents, source: "paymentElement")
                    }
                )
                paymentElementref.shouldProceedWithPayment { data, callback in
                    self.onPaymentConfirmCallback = callback
                    do {
                        let data = try JSONEncoder().encode(data)
                        let dictionary =
                            try JSONSerialization.jsonObject(
                                with: data
                            ) as? [String: Any]
                        self.fireEvent(
                            type: "onPaymentConfirmButtonClickEvent",
                            payload: dictionary,
                            source: "onPaymentConfirmButtonClickEvent"
                        )

                    } catch {
                        print(error)
                    }
                }
                print("[Hyperswitch] PaymentElement created and placed successfully")

            } else if lower == "cvcwidget" || lower == "cvc" {
                guard let container = self.cvcWidgetContainer else {
                    print("[Hyperswitch] CVCWidget container not registered — call create() first")
                    return
                }
                container.attach(
                    hyperswitch: hyperswitch,
                    paymentSession: paymentSession,
                    configuration: configMap
                ) { [weak self] builder in
                    builder.on(.cvcStatus) { event in
                        self?.fireEvent(type: PaymentEventType.cvcStatus.rawValue, payload: event.payload, source: "cvcWidget")
                    }
                }
                print("[Hyperswitch] CVCWidget created and placed successfully")
            }
        }
    }
    func resolvePaymentConfirmButtonClick(proceed: Bool) {
        let cb = self.onPaymentConfirmCallback
        if cb != nil {
            cb?(proceed)
        }
    }

    private func bindPaymentElementEvents(
        builder: PaymentEventSubscriptionBuilder,
        subscribed: [String],
        source: String
    ) {
        if subscribed.contains(PaymentEventType.formStatus.rawValue) {
            builder.on(.formStatus) { [weak self] event in
                self?.fireEvent(type: PaymentEventType.formStatus.rawValue, payload: event.payload, source: source)
            }
        }
        if subscribed.contains(PaymentEventType.paymentMethodStatus.rawValue) {
            builder.on(.paymentMethodStatus) { [weak self] event in
                self?.fireEvent(type: PaymentEventType.paymentMethodStatus.rawValue, payload: event.payload, source: source)
            }
        }
        if subscribed.contains(PaymentEventType.paymentMethodInfoCard.rawValue) {
            builder.on(.paymentMethodInfoCard) { [weak self] event in
                self?.fireEvent(type: PaymentEventType.paymentMethodInfoCard.rawValue, payload: event.payload, source: source)
            }
        }
        if subscribed.contains(PaymentEventType.paymentMethodInfoBillingAddress.rawValue) {
            builder.on(.paymentMethodInfoBillingAddress) { [weak self] event in
                self?.fireEvent(type: PaymentEventType.paymentMethodInfoBillingAddress.rawValue, payload: event.payload, source: source)
            }
        }

        if subscribed.contains(PaymentEventType.cvcStatus.rawValue) {
            builder.on(.cvcStatus) { [weak self] event in
                self?.fireEvent(type: PaymentEventType.paymentMethodInfoBillingAddress.rawValue, payload: event.payload, source: source)
            }
        }
    }

    private static func extractAndRemoveSubscribedEvents(_ map: inout [String: Any]) -> [String] {
        guard let raw = map.removeValue(forKey: "subscribedEvents") else { return [] }
        if let arr = raw as? [String] { return arr }
        if let arr = raw as? [Any] { return arr.compactMap { $0 as? String } }
        return []
    }

    // ── updateIntent ───────────────────────────────────────────────────────────

    func updateIntent(
        sdkAuthorization: String,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let paymentSession = self.paymentSession else {
            onError("elements() must be called first")
            return
        }

        paymentSession.updateIntent(
            authorizationProvider: { authorizationProvider in
                authorizationProvider(sdkAuthorization)
            },
            completion: { result in
                switch result {
                case .success:
                    onResult(["type": "success", "message": "Success"])
                case .cancelled:
                    onResult(["type": "cancelled", "message": "Cancelled"])
                case .failure(let error):
                    onResult(["type": "failure", "message": "\(error.localizedDescription)"])
                }
            }
        )
    }
    // ── initPaymentSession (legacy) ────────────────────────────────────────────

    func initPaymentSession(
        sdkAuthorization: String,
        onReady: @escaping VoidCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let hyperswitch = hyperswitch else {
            onError("Hyperswitch not initialised — call init() first")
            return
        }
        let paymentSessionConfiguration = PaymentSessionConfiguration(sdkAuthorization: sdkAuthorization)
        self.paymentSession = hyperswitch.initPaymentSession(configuration: paymentSessionConfiguration)
        currentSdkAuthorization = sdkAuthorization
        onReady()
    }

    // ── presentPaymentSheet (legacy) ───────────────────────────────────────────

    func presentPaymentSheet(
        viewController: UIViewController,
        sheetOptions: [String: Any]?,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let paymentSession = paymentSession else {
            onError("Payment session not initialised — call initPaymentSession first")
            return
        }

        let params: [String: Any] = sheetOptions ?? [:]

        DispatchQueue.main.async {
            paymentSession.presentPaymentSheetWithParams(viewController: viewController, params: params) {
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
            paymentWidget.confirm()
            paymentElementConfirm = onResult
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
        return serializePaymentMethodData(data)
    }

    func getCustomerDefaultSavedPaymentMethodData(handlerId: String) -> [String: Any] {
        guard let handler = handlerRegistry[handlerId] else {
            print("[Hyperswitch] No handler for id: \(handlerId)")
            return [:]
        }
        let data = handler.getCustomerDefaultSavedPaymentMethodData()
        return serializePaymentMethodData(data)
    }

    func getCustomerLastUsedPaymentMethodData(handlerId: String) -> [String: Any] {
        guard let handler = handlerRegistry[handlerId] else {
            print("[Hyperswitch] No handler for id: \(handlerId)")
            return [:]
        }
        let result = handler.getCustomerLastUsedPaymentMethodData()
        switch result {
        case .success(let paymentMethod):
            if let jsonData = try? JSONEncoder().encode(paymentMethod),
                let dict = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any]
            {
                return ["data": dict]
            }
            return ["error": ["code": "ERROR", "message": "Failed to serialize PaymentMethod"]]
        case .failure(let pmError):
            if let jsonData = try? JSONEncoder().encode(pmError),
                let dict = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any]
            {
                return ["error": dict]
            }
            return ["error": ["code": pmError.code, "message": pmError.message]]
        }
    }

    // ── Handler-scoped confirm methods ─────────────────────────────────────────

    func confirmWithCustomerDefaultPaymentMethod(
        handlerId: String,
        onResult: @escaping PaymentResultCallback,
        onError: @escaping ErrorCallback
    ) {
        guard let handler = handlerRegistry[handlerId] else {
            onError("No handler for id: \(handlerId)")
            return
        }
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            handler.confirmWithCustomerDefaultPaymentMethod { result in
                onResult(self.paymentResultToDict(result))
            }
        }
    }

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

    private func serializePaymentMethodData(_ data: Any?) -> [String: Any] {
        guard let data = data else { return [:] }
        if let dict = data as? [String: Any] {
            if let jsonData = try? JSONSerialization.data(withJSONObject: dict),
                let jsonString = String(data: jsonData, encoding: .utf8)
            {
                return ["data": jsonString]
            }
            return ["data": dict]
        }
        if let arr = data as? [Any] {
            if let jsonData = try? JSONSerialization.data(withJSONObject: arr),
                let jsonString = String(data: jsonData, encoding: .utf8)
            {
                return ["data": jsonString]
            }
        }
        return ["data": "\(data)"]
    }

    private func paymentResultToDict(_ result: PaymentResult) -> [String: Any] {
        switch result {
        case .completed(let data):
            return ["type": "completed", "message": data]
        case .canceled(let data):
            return ["type": "canceled", "message": data]
        case .failed(let error):
            return ["type": "failed", "message": error.localizedDescription]
        @unknown default:
            return ["type": "failed", "message": "Unknown result"]
        }
    }
}
