import Capacitor
import Foundation
import Hyperswitch
import UIKit
import WebKit

/// iOS counterpart to Android's `CvcWidgetPlugin.java`.
@objc(CvcWidgetPlugin)
public class CvcWidgetPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CvcWidgetPlugin"
    public let jsName = "CvcWidget"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "create", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "destroy", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updatePosition", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hide", returnType: CAPPluginReturnPromise),
    ]

    private var cvcWidgetView: CVCWidget?

    // ── create ─────────────────────────────────────────────────────────────────

    @objc func create(_ call: CAPPluginCall) {
        guard let x = call.getFloat("x"),
            let y = call.getFloat("y"),
            let width = call.getFloat("width"),
            let height = call.getFloat("height")
        else {
            call.reject("x, y, width, and height are required")
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.removeWidgetView()

            guard let webView = self.webView else {
                call.reject("Unable to access webView")
                return
            }

            let frame = CGRect(
                x: CGFloat(x),
                y: CGFloat(y),
                width: CGFloat(width),
                height: CGFloat(height)
            )

            // Register a callback so that when createElement creates the widget
            // with payment session data, we can place it into the scrollView.
            HyperswitchImpl.shared.setPendingCvcViewCallback { [weak self] view in
                view.frame = frame
                webView.scrollView.addSubview(view)
                self?.cvcWidgetView = view as? CVCWidget
            }

            call.resolve()
        }
    }

    // ── destroy ────────────────────────────────────────────────────────────────

    @objc func destroy(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.removeWidgetView()
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
            guard let view = self?.cvcWidgetView else {
                call.reject("No CVC widget view to update")
                return
            }

            view.frame = CGRect(
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
            self?.cvcWidgetView?.isHidden = false
            call.resolve()
        }
    }

    @objc func hide(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.cvcWidgetView?.isHidden = true
            call.resolve()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private func removeWidgetView() {
        cvcWidgetView?.removeFromSuperview()
        cvcWidgetView = nil
        HyperswitchImpl.shared.registerCvcWidgetView(nil)
    }
}
