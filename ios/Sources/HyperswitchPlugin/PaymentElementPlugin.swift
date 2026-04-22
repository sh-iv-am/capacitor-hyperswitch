import Foundation
import Capacitor
import UIKit
import WebKit
import Hyperswitch

/// iOS counterpart to Android's `PaymentElementPlugin.java`.
///
/// On iOS the PaymentElement view is added to `webView.scrollView` at the
/// content-coordinate position reported by JS, so it scrolls with the page
/// automatically — no manual scroll-offset tracking needed.
/// `show` / `hide` are called by the JS IntersectionObserver when the
/// placeholder enters or leaves the viewport.
@objc(PaymentElementPlugin)
public class PaymentElementPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "PaymentElementPlugin"
    public let jsName = "PaymentElement"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "create",         returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "destroy",        returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updatePosition", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show",           returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hide",           returnType: CAPPluginReturnPromise),
    ]

    private var paymentElementView: PaymentElement?

    // ── create ─────────────────────────────────────────────────────────────────

    @objc func create(_ call: CAPPluginCall) {
        guard let x      = call.getFloat("x"),
              let y      = call.getFloat("y"),
              let width  = call.getFloat("width"),
              let height = call.getFloat("height") else {
            call.reject("x, y, width, and height are required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            // Remove any existing view first
            self.removeElementView()

            guard let webView = self.webView else {
                call.reject("Unable to access webView")
                return
            }

            let view = PaymentElement(frame: CGRect(
                x: CGFloat(x), y: CGFloat(y),
                width: CGFloat(width), height: CGFloat(height)
            ))

            // Add to scrollView so the view travels with page content
            webView.scrollView.addSubview(view)

            self.paymentElementView = view
            HyperswitchImpl.shared.registerPaymentElementView(view)

            call.resolve()
        }
    }

    // ── destroy ────────────────────────────────────────────────────────────────

    @objc func destroy(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.removeElementView()
            call.resolve()
        }
    }

    // ── updatePosition ─────────────────────────────────────────────────────────

    @objc func updatePosition(_ call: CAPPluginCall) {
        guard let x      = call.getFloat("x"),
              let y      = call.getFloat("y"),
              let width  = call.getFloat("width"),
              let height = call.getFloat("height") else {
            call.reject("x, y, width, and height are required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let view = self?.paymentElementView else {
                call.reject("No payment element view to update")
                return
            }

            view.frame = CGRect(
                x: CGFloat(x), y: CGFloat(y),
                width: CGFloat(width), height: CGFloat(height)
            )
            call.resolve()
        }
    }

    // ── show / hide ────────────────────────────────────────────────────────────

    @objc func show(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.paymentElementView?.isHidden = false
            call.resolve()
        }
    }

    @objc func hide(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.paymentElementView?.isHidden = true
            call.resolve()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private func removeElementView() {
        paymentElementView?.removeFromSuperview()
        paymentElementView = nil
        HyperswitchImpl.shared.registerPaymentElementView(nil)
    }
}
