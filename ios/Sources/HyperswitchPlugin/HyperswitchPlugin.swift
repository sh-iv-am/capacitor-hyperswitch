import Foundation
import Capacitor

@objc(HyperswitchPlugin)
public class HyperswitchPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "HyperswitchPlugin"
    public let jsName = "Hyperswitch"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "init",                                    returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elements",                                returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "createElement",                           returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateIntent",                            returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "initPaymentSession",                      returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "presentPaymentSheet",                     returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCustomerSavedPaymentMethods",          returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCustomerSavedPaymentMethodData",       returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCustomerDefaultSavedPaymentMethodData",returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCustomerLastUsedPaymentMethodData",    returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "confirmWithCustomerDefaultPaymentMethod", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "confirmWithCustomerLastUsedPaymentMethod",returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "confirmPayment",                          returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementOn",                               returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementCollapse",                         returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementBlur",                             returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementUpdate",                           returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementDestroy",                          returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementUnmount",                          returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementMount",                            returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementFocus",                            returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "elementClear",                            returnType: CAPPluginReturnPromise),
    ]

    private let impl = HyperswitchImpl.shared

    // ── Init ───────────────────────────────────────────────────────────────────

    @objc func `init`(_ call: CAPPluginCall) {
        guard let publishableKey = call.getString("publishableKey"), !publishableKey.isEmpty else {
            call.reject("publishableKey is required")
            return
        }

        guard let viewController = bridge?.viewController else {
            call.reject("Unable to get view controller from bridge")
            return
        }

        let profileId    = call.getString("profileId")
        let customConfig = call.getObject("customConfig")
        let environment  = call.getString("environment") ?? "PROD"

        impl.initialize(
            viewController: viewController,
            publishableKey: publishableKey,
            profileId: profileId,
            customConfig: customConfig,
            environment: environment
        )
        call.resolve()
    }

    // ── Elements ───────────────────────────────────────────────────────────────

    @objc func elements(_ call: CAPPluginCall) {
        let options = call.getObject("elementsOptions")
        let sdkAuthorization = options?["sdkAuthorization"] as? String ?? ""

        impl.elements(
            sdkAuthorization: sdkAuthorization,
            onReady: { handlerId in call.resolve(["handlerId": handlerId]) },
            onError: { msg in call.reject(msg) }
        )
    }

    @objc func createElement(_ call: CAPPluginCall) {
        let type          = call.getString("type") ?? ""
        let createOptions = call.getObject("createOptions")
        impl.createElement(type: type, createOptions: createOptions)
        call.resolve()
    }

    @objc func updateIntent(_ call: CAPPluginCall) {
        let sdkAuthorization = call.getString("sdkAuthorization") ?? ""

        impl.updateIntent(
            sdkAuthorization: sdkAuthorization,
            onResult: { result in call.resolve(result) },
            onError:  { msg in call.reject(msg) }
        )
    }

    // ── InitPaymentSession ─────────────────────────────────────────────────────

    @objc func initPaymentSession(_ call: CAPPluginCall) {
        let options          = call.getObject("paymentSessionOptions")
        let sdkAuthorization = options?["sdkAuthorization"] as? String ?? ""

        impl.initPaymentSession(
            sdkAuthorization: sdkAuthorization,
            onReady: { call.resolve() },
            onError: { msg in call.reject(msg) }
        )
    }

    @objc func presentPaymentSheet(_ call: CAPPluginCall) {
        guard let viewController = bridge?.viewController else {
            call.reject("Unable to get view controller from bridge")
            return
        }

        impl.presentPaymentSheet(
            viewController: viewController,
            onResult: { result in call.resolve(result) },
            onError:  { msg in call.reject(msg) }
        )
    }

    // ── Saved payment methods ──────────────────────────────────────────────────

    @objc func getCustomerSavedPaymentMethods(_ call: CAPPluginCall) {
        impl.getCustomerSavedPaymentMethods(
            onReady: { handlerId in call.resolve(["handlerId": handlerId]) },
            onError: { msg in call.reject(msg) }
        )
    }

    @objc func getCustomerSavedPaymentMethodData(_ call: CAPPluginCall) {
        guard let handlerId = call.getString("handlerId"), !handlerId.isEmpty else {
            call.reject("handlerId is required"); return
        }
        call.resolve(impl.getCustomerSavedPaymentMethodData(handlerId: handlerId))
    }

    @objc func getCustomerDefaultSavedPaymentMethodData(_ call: CAPPluginCall) {
        guard let handlerId = call.getString("handlerId"), !handlerId.isEmpty else {
            call.reject("handlerId is required"); return
        }
        call.resolve(impl.getCustomerDefaultSavedPaymentMethodData(handlerId: handlerId))
    }

    @objc func getCustomerLastUsedPaymentMethodData(_ call: CAPPluginCall) {
        guard let handlerId = call.getString("handlerId"), !handlerId.isEmpty else {
            call.reject("handlerId is required"); return
        }
        call.resolve(impl.getCustomerLastUsedPaymentMethodData(handlerId: handlerId))
    }

    // ── Confirm with saved methods ─────────────────────────────────────────────

    @objc func confirmWithCustomerDefaultPaymentMethod(_ call: CAPPluginCall) {
        guard let handlerId = call.getString("handlerId"), !handlerId.isEmpty else {
            call.reject("handlerId is required"); return
        }
        impl.confirmWithCustomerDefaultPaymentMethod(
            handlerId: handlerId,
            onResult: { result in call.resolve(result) },
            onError:  { msg in call.reject(msg) }
        )
    }

    @objc func confirmWithCustomerLastUsedPaymentMethod(_ call: CAPPluginCall) {
        guard let handlerId = call.getString("handlerId"), !handlerId.isEmpty else {
            call.reject("handlerId is required"); return
        }
        impl.confirmWithCustomerLastUsedPaymentMethod(
            handlerId: handlerId,
            onResult: { result in call.resolve(result) },
            onError:  { msg in call.reject(msg) }
        )
    }

    // ── confirmPayment ─────────────────────────────────────────────────────────

    @objc func confirmPayment(_ call: CAPPluginCall) {
        let confirmParams = call.getObject("confirmParams")

        impl.confirmPayment(
            confirmParams: confirmParams,
            onResult: { result in call.resolve(result) },
            onError:  { msg in call.reject(msg) }
        )
    }

    // ── PaymentElement lifecycle stubs ─────────────────────────────────────────
    // These are no-ops on iOS; mounting/unmounting is handled natively by
    // PaymentElementPlugin. They resolve immediately so JS callers don't hang.

    @objc func elementMount(_ call: CAPPluginCall)    { call.resolve() }
    @objc func elementOn(_ call: CAPPluginCall)       { call.resolve() }
    @objc func elementCollapse(_ call: CAPPluginCall) { call.resolve() }
    @objc func elementBlur(_ call: CAPPluginCall)     { call.resolve() }
    @objc func elementFocus(_ call: CAPPluginCall)    { call.resolve() }
    @objc func elementClear(_ call: CAPPluginCall)    { call.resolve() }
    @objc func elementUpdate(_ call: CAPPluginCall)   { call.resolve() }
    @objc func elementUnmount(_ call: CAPPluginCall)  { call.resolve() }
    @objc func elementDestroy(_ call: CAPPluginCall)  { call.resolve() }
}
