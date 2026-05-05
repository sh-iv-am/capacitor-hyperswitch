package io.hyperswitch.capacitor;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import io.hyperswitch.view.PaymentElement;

@CapacitorPlugin(name = "PaymentElement")
public class PaymentElementPlugin extends Plugin {

    private PaymentElement paymentElement;

    private int contentX;
    private int contentY;

    private View.OnScrollChangeListener scrollListener;
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

    private int[] getWebViewOffset(WebView webView) {
        int[] pos = new int[2];
        webView.getLocationInWindow(pos);
        return pos;
    }

    @PluginMethod
    public void create(PluginCall call) {
        Float x = call.getFloat("x");
        Float y = call.getFloat("y");
        Float width = call.getFloat("width");
        Float height = call.getFloat("height");

        if (x == null || y == null || width == null || height == null) {
            call.reject("x, y, width, and height are required");
            return;
        }

        float density = getContext().getResources().getDisplayMetrics().density;

        contentX = Math.round(x * density);
        contentY = Math.round(y * density);
        int pxWidth = Math.round(width * density);
        int pxHeight = Math.round(height * density);

        getActivity().runOnUiThread(() -> {
            removeElementView();

            WebView webView = getBridge().getWebView();
            ViewGroup parent = (ViewGroup) webView.getParent();

            if (parent == null) {
                call.reject("Unable to access webView parent");
                return;
            }

            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            PaymentElement view = new PaymentElement(getContext());

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(pxWidth, pxHeight);
            parent.addView(view, params);

            int[] offset = getWebViewOffset(webView);
            view.setX(contentX - webView.getScrollX() + offset[0]);
            view.setY(contentY - webView.getScrollY() + offset[1]);

            updateVisibility(view, webView);

            paymentElement = view;
            HyperswitchImpl.getInstance().registerPaymentElementView(view);

            scrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (paymentElement != null) {
                    int[] off = getWebViewOffset(webView);
                    paymentElement.setX(contentX - scrollX + off[0]);
                    paymentElement.setY(contentY - scrollY + off[1]);
                    updateVisibility(paymentElement, webView);
                }
            };
            webView.setOnScrollChangeListener(scrollListener);

            globalLayoutListener = () -> {
                if (paymentElement != null) {
                    int[] off = getWebViewOffset(webView);
                    paymentElement.setX(contentX - webView.getScrollX() + off[0]);
                    paymentElement.setY(contentY - webView.getScrollY() + off[1]);
                    updateVisibility(paymentElement, webView);
                }
            };
            webView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

            call.resolve();
        });
    }

    @PluginMethod
    public void destroy(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            removeElementView();
            call.resolve();
        });
    }

    @PluginMethod
    public void updatePosition(PluginCall call) {
        Float x = call.getFloat("x");
        Float y = call.getFloat("y");
        Float width = call.getFloat("width");
        Float height = call.getFloat("height");

        if (x == null || y == null || width == null || height == null) {
            call.reject("x, y, width, and height are required");
            return;
        }

        float density = getContext().getResources().getDisplayMetrics().density;

        contentX = Math.round(x * density);
        contentY = Math.round(y * density);
        int pxWidth = Math.round(width * density);
        int pxHeight = Math.round(height * density);

        getActivity().runOnUiThread(() -> {
            if (paymentElement == null) {
                call.reject("No payment element view to update");
                return;
            }

            WebView webView = getBridge().getWebView();

            ViewGroup.LayoutParams params = paymentElement.getLayoutParams();
            params.width = pxWidth;
            params.height = pxHeight;
            paymentElement.setLayoutParams(params);

            int[] offset = getWebViewOffset(webView);
            paymentElement.setX(contentX - webView.getScrollX() + offset[0]);
            paymentElement.setY(contentY - webView.getScrollY() + offset[1]);

            updateVisibility(paymentElement, webView);

            call.resolve();
        });
    }

    @PluginMethod
    public void show(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (paymentElement != null) paymentElement.setVisibility(View.VISIBLE);
            call.resolve();
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (paymentElement != null) paymentElement.setVisibility(View.GONE);
            call.resolve();
        });
    }

    private void removeElementView() {
        if (paymentElement != null && paymentElement.getParent() != null) {
            ((ViewGroup) paymentElement.getParent()).removeView(paymentElement);
        }
        paymentElement = null;
        HyperswitchImpl.getInstance().registerPaymentElementView(null);

        if (scrollListener != null) {
            WebView webView = getBridge().getWebView();
            if (webView != null) webView.setOnScrollChangeListener(null);
            scrollListener = null;
        }

        if (globalLayoutListener != null) {
            WebView webView = getBridge().getWebView();
            if (webView != null) webView.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            globalLayoutListener = null;
        }
    }

    private void updateVisibility(View view, WebView webView) {
        int[] offset = getWebViewOffset(webView);
        float relativeX = view.getX() - offset[0] + webView.getScrollX();
        float relativeY = view.getY() - offset[1] + webView.getScrollY();
        int viewWidth = view.getLayoutParams().width;
        int viewHeight = view.getLayoutParams().height;

        boolean offscreen = (relativeX + viewWidth <= 0)
                || (relativeY + viewHeight <= 0)
                || (relativeX >= webView.getWidth())
                || (relativeY >= webView.getHeight());

        view.setVisibility(offscreen ? View.GONE : View.VISIBLE);
    }
}
