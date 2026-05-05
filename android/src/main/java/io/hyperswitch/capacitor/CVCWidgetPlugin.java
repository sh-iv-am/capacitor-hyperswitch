package io.hyperswitch.capacitor;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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

    private int lastOffsetX = -1;
    private int lastOffsetY = -1;

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

            int[] offset = getWebViewOffset(webView);
            lastOffsetX = offset[0];
            lastOffsetY = offset[1];
            view.setX(contentX - webView.getScrollX() + offset[0]);
            view.setY(contentY - webView.getScrollY() + offset[1]);

            updateVisibility(view, webView);

            cvcWidget = view;
            HyperswitchImpl.getInstance().registerCVCWidgetView(view);

            scrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (cvcWidget != null) {
                    int[] off = getWebViewOffset(webView);
                    cvcWidget.setX(contentX - scrollX + off[0]);
                    cvcWidget.setY(contentY - scrollY + off[1]);
                    updateVisibility(cvcWidget, webView);
                }
            };
            webView.setOnScrollChangeListener(scrollListener);

            globalLayoutListener = () -> {
                if (cvcWidget != null) {
                    int[] off = getWebViewOffset(webView);
                    if (off[0] != lastOffsetX || off[1] != lastOffsetY) {
                        lastOffsetX = off[0];
                        lastOffsetY = off[1];
                        cvcWidget.setX(contentX - webView.getScrollX() + off[0]);
                        cvcWidget.setY(contentY - webView.getScrollY() + off[1]);
                        updateVisibility(cvcWidget, webView);
                    }
                }
            };
            webView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

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

            int[] offset = getWebViewOffset(webView);
            lastOffsetX = offset[0];
            lastOffsetY = offset[1];
            cvcWidget.setX(contentX - webView.getScrollX() + offset[0]);
            cvcWidget.setY(contentY - webView.getScrollY() + offset[1]);

            updateVisibility(cvcWidget, webView);

            call.resolve();
        });
    }

    @PluginMethod
    public void show(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (cvcWidget != null) cvcWidget.setVisibility(View.VISIBLE);
            call.resolve();
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (cvcWidget != null) cvcWidget.setVisibility(View.GONE);
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

        if (globalLayoutListener != null) {
            WebView webView = getBridge().getWebView();
            if (webView != null) webView.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            globalLayoutListener = null;
        }

        lastOffsetX = -1;
        lastOffsetY = -1;
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
