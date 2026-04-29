import Capacitor
import Foundation
import Hyperswitch
import UIKit
import WebKit

@objc(PaymentElementPlugin)
public class PaymentElementPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "PaymentElementPlugin"
    public let jsName = "PaymentElement"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "create", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "destroy", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updatePosition", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hide", returnType: CAPPluginReturnPromise),
    ]

    private var container: PaymentElementContainer?

    // ── create ─────────────────────────────────────────────────────────────────

    @objc func create(_ call: CAPPluginCall) {
        guard
            let x = call.getFloat("x"),
            let y = call.getFloat("y"),
            let width = call.getFloat("width"),
            let height = call.getFloat("height")
        else {
            call.reject("x, y, width, and height are required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.removePaymentElementContainer()

            guard let webView = self.webView else {
                call.reject("unable to access webView")
                return
            }

            let frame = CGRect(
                x: CGFloat(x),
                y: CGFloat(y),
                width: CGFloat(width),
                height: CGFloat(height)
            )

            let container = PaymentElementContainer(frame: frame)
            webView.scrollView.addSubview(container)
            self.container = container
            HyperswitchImpl.shared.registerPaymentElementContainer(container)

            call.resolve()
        }
    }

    // ── destroy ────────────────────────────────────────────────────────────────

    @objc func destroy(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.removePaymentElementContainer()
            call.resolve()
        }
    }

    // ── updatePosition ─────────────────────────────────────────────────────────

    @objc func updatePosition(_ call: CAPPluginCall) {
        guard let x = call.getFloat("x"),
            let y = call.getFloat("y"),
            let width = call.getFloat("width"),
            let height = call.getFloat("height")
        else {
            call.reject("x, y, width, and height are required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let container = self?.container else {
                call.reject("no payment element view to update")
                return
            }

            container.frame = CGRect(
                x: CGFloat(x),
                y: CGFloat(y),
                width: CGFloat(width),
                height: CGFloat(height)
            )
            call.resolve()
        }
    }

    // ── show / hide ────────────────────────────────────────────────────────────

    @objc func show(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.container?.isHidden = false
            call.resolve()
        }
    }

    @objc func hide(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.container?.isHidden = true
            call.resolve()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private func removePaymentElementContainer() {
        container?.detach()
        container?.removeFromSuperview()
        container = nil
        HyperswitchImpl.shared.registerPaymentElementContainer(nil)
    }
}
