package io.hyperswitch.capacitor;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Map;

@CapacitorPlugin(name = "Hyperswitch")
public class HyperswitchPlugin extends Plugin {

    private final HyperswitchImpl implementation = HyperswitchImpl.getInstance();

    /**
     * Called once after the plugin is registered with Capacitor.
     * Registers a NativeEventListener so that widget events (FORM_STATUS, CVC_STATUS, etc.)
     * are forwarded to the JS layer via notifyListeners("paymentEvent", ...).
     */
    @Override
    public void load() {
        implementation.setEventListener((type, payload, source) -> {
            JSObject data = new JSObject();
            data.put("type", type);
            JSObject payloadJs = new JSObject();
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    payloadJs.put(entry.getKey(), value.toString());
                }
            }
            data.put("payload", payloadJs);
            String channel = switch (source) {
                case "paymentElement" -> "paymentElementEvent";
                case "cvcWidget" -> "cvcWidgetEvent";
                default -> "paymentEvent";
            };
            notifyListeners(channel, data);
        });
    }

    @PluginMethod
    public void init(PluginCall call) {
        String publishableKey = call.getString("publishableKey");

        if (publishableKey == null || publishableKey.isEmpty()) {
            call.reject("publishableKey is required");
            return;
        }

        String profileId = call.getString("profileId");
        JSObject customConfig = call.getObject("customConfig");
        String environment = call.getString("environment", "PROD");

        implementation.init(getActivity(), publishableKey, profileId, customConfig, environment);
        call.resolve();
    }

    @PluginMethod
    public void elements(PluginCall call) {
        call.setKeepAlive(true);
        JSObject elementsOptions = call.getObject("elementsOptions");
        implementation.elements(elementsOptions, new HyperswitchImpl.ElementsCallback() {
            @Override
            public void onReady(String handlerId) {
                JSObject result = new JSObject();
                result.put("handlerId", handlerId);
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void createElement(PluginCall call) {
        String type = call.getString("type");
        JSObject createOptions = call.getObject("createOptions");
        implementation.createElement(type, createOptions);
        call.resolve();
    }

    @PluginMethod
    public void updateIntent(PluginCall call) {
        call.setKeepAlive(true);
        String sdkAuthorization = call.getString("sdkAuthorization");
        implementation.updateIntent(sdkAuthorization, new HyperswitchImpl.PaymentResultCallback() {
            @Override
            public void onResult(JSObject result) {
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void initPaymentSession(PluginCall call) {
        call.setKeepAlive(true);
        JSObject paymentSessionOptions = call.getObject("paymentSessionOptions");
        implementation.initPaymentSession(paymentSessionOptions, new HyperswitchImpl.InitPaymentSessionCallback() {
            @Override
            public void onReady() {
                call.setKeepAlive(false);
                call.resolve();
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void getCustomerSavedPaymentMethods(PluginCall call) {
        call.setKeepAlive(true);
        implementation.getCustomerSavedPaymentMethods(new HyperswitchImpl.CustomerSavedPaymentMethodsCallback() {
            @Override
            public void onReady(String handlerId) {
                JSObject result = new JSObject();
                result.put("handlerId", handlerId);
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void getCustomerSavedPaymentMethodData(PluginCall call) {
        String handlerId = call.getString("handlerId");
        if (handlerId == null || handlerId.isEmpty()) { call.reject("handlerId is required"); return; }
        call.resolve(implementation.getCustomerSavedPaymentMethodData(handlerId));
    }

    @PluginMethod
    public void getCustomerDefaultSavedPaymentMethodData(PluginCall call) {
        String handlerId = call.getString("handlerId");
        if (handlerId == null || handlerId.isEmpty()) { call.reject("handlerId is required"); return; }
        call.resolve(implementation.getCustomerDefaultSavedPaymentMethodData(handlerId));
    }

    @PluginMethod
    public void getCustomerLastUsedPaymentMethodData(PluginCall call) {
        String handlerId = call.getString("handlerId");
        if (handlerId == null || handlerId.isEmpty()) { call.reject("handlerId is required"); return; }
        call.resolve(implementation.getCustomerLastUsedPaymentMethodData(handlerId));
    }

    @PluginMethod
    public void presentPaymentSheet(PluginCall call) {
        call.setKeepAlive(true);
        JSObject sheetOptions = call.getObject("sheetOptions");
        implementation.presentPaymentSheet(sheetOptions, new HyperswitchImpl.PaymentSheetCallback() {
            @Override
            public void onResult(JSObject result) {
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(Exception e) {
                call.setKeepAlive(false);
                call.reject(e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void confirmPayment(PluginCall call) {
        call.setKeepAlive(true);
        JSObject confirmParams = call.getObject("confirmParams");
        implementation.confirmPayment(confirmParams, new HyperswitchImpl.PaymentResultCallback() {
            @Override
            public void onResult(JSObject result) {
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void confirmWithCustomerDefaultPaymentMethod(PluginCall call) {
        String handlerId = call.getString("handlerId");
        if (handlerId == null || handlerId.isEmpty()) { call.reject("handlerId is required"); return; }
        call.setKeepAlive(true);
        implementation.confirmWithCustomerDefaultPaymentMethod(handlerId, new HyperswitchImpl.PaymentResultCallback() {
            @Override
            public void onResult(JSObject result) {
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void confirmWithCustomerLastUsedPaymentMethod(PluginCall call) {
        String handlerId = call.getString("handlerId");
        if (handlerId == null || handlerId.isEmpty()) { call.reject("handlerId is required"); return; }
        call.setKeepAlive(true);
        implementation.confirmWithCustomerLastUsedPaymentMethod(handlerId, new HyperswitchImpl.PaymentResultCallback() {
            @Override
            public void onResult(JSObject result) {
                call.setKeepAlive(false);
                call.resolve(result);
            }

            @Override
            public void onError(String message) {
                call.setKeepAlive(false);
                call.reject(message);
            }
        });
    }

    // ── PaymentElement lifecycle stubs ────────────────────────────────────────

    @PluginMethod
    public void elementMount(PluginCall call) {
        // Mounting is handled natively via PaymentElementPlugin.create() + createElement().
        call.resolve();
    }

    @PluginMethod
    public void elementOn(PluginCall call) {
        // Event subscriptions are registered natively when the element is bound (createElement).
        // Events are pushed to JS via notifyListeners("paymentEvent", ...).
        // This method is kept as a no-op so the JS bridge doesn't reject the call.
        call.resolve();
    }

    @PluginMethod
    public void elementCollapse(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void elementBlur(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void elementFocus(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void elementClear(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void elementUpdate(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void elementUnmount(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void elementDestroy(PluginCall call) {
        call.resolve();
    }
}
