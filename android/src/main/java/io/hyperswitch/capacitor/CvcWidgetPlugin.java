package io.hyperswitch.capacitor;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import io.hyperswitch.view.CVCWidget;

@CapacitorPlugin(name = "CVCWidget")
public class CVCWidgetPlugin extends Plugin {

    private CVCWidget cvcWidget;

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
            removeWidgetView();

            WebView webView = getBridge().getWebView();
            ViewGroup parent = (ViewGroup) webView.getParent();

            if (parent == null) {
                call.reject("Unable to access webView parent");
                return;
            }

            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            CVCWidget view = new CVCWidget(getContext());

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(pxWidth, pxHeight);
            parent.addView(view, params);

            view.setX(contentX - webView.getScrollX());
            view.setY(contentY - webView.getScrollY());

            updateVisibility(view, webView);

            cvcWidget = view;
            HyperswitchImpl.getInstance().registerCVCWidgetView(view);

            scrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (cvcWidget != null) {
                    cvcWidget.setX(contentX - scrollX);
                    cvcWidget.setY(contentY - scrollY);
                    updateVisibility(cvcWidget, webView);
                }
            };
            webView.setOnScrollChangeListener(scrollListener);

            call.resolve();
        });
    }

    @PluginMethod
    public void destroy(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            removeWidgetView();
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
            if (cvcWidget == null) {
                call.reject("No CVC widget view to update");
                return;
            }

            WebView webView = getBridge().getWebView();

            ViewGroup.LayoutParams params = cvcWidget.getLayoutParams();
            params.width = pxWidth;
            params.height = pxHeight;
            cvcWidget.setLayoutParams(params);

            cvcWidget.setX(contentX - webView.getScrollX());
            cvcWidget.setY(contentY - webView.getScrollY());

            updateVisibility(cvcWidget, webView);

            call.resolve();
        });
    }

    private void removeWidgetView() {
        if (cvcWidget != null && cvcWidget.getParent() != null) {
            ((ViewGroup) cvcWidget.getParent()).removeView(cvcWidget);
        }
        cvcWidget = null;
        HyperswitchImpl.getInstance().registerCVCWidgetView(null);

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
