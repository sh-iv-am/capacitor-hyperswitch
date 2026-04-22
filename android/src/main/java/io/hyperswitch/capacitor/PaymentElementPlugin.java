package io.hyperswitch.capacitor;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
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

            view.setX(contentX - webView.getScrollX());
            view.setY(contentY - webView.getScrollY());

            updateVisibility(view, webView);

            paymentElement = view;
            HyperswitchImpl.getInstance().registerPaymentElementView(view);

            scrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (paymentElement != null) {
                    paymentElement.setX(contentX - scrollX);
                    paymentElement.setY(contentY - scrollY);
                    updateVisibility(paymentElement, webView);
                }
            };
            webView.setOnScrollChangeListener(scrollListener);

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

            paymentElement.setX(contentX - webView.getScrollX());
            paymentElement.setY(contentY - webView.getScrollY());

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
    }

    private void updateVisibility(View view, WebView webView) {
        float viewX = view.getX();
        float viewY = view.getY();
        int viewWidth = view.getLayoutParams().width;
        int viewHeight = view.getLayoutParams().height;
        int parentWidth = webView.getWidth();
        int parentHeight = webView.getHeight();

        boolean offscreen = (viewX + viewWidth <= 0)
                || (viewY + viewHeight <= 0)
                || (viewX >= parentWidth)
                || (viewY >= parentHeight);

        view.setVisibility(offscreen ? View.GONE : View.VISIBLE);
    }
}
