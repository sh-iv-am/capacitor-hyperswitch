package io.hyperswitch.capacitor;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Hyperswitch")
public class HyperswitchPlugin extends Plugin {

    private final HyperswitchImpl implementation = HyperswitchImpl.getInstance();

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
        JSObject result = implementation.getCustomerSavedPaymentMethods();
        call.resolve(result);
    }

    @PluginMethod
    public void getCustomerDefaultSavedPaymentMethodData(PluginCall call) {
        JSObject result = implementation.getCustomerDefaultSavedPaymentMethodData();
        call.resolve(result);
    }

    @PluginMethod
    public void getCustomerLastUsedPaymentMethodData(PluginCall call) {
        JSObject result = implementation.getCustomerLastUsedPaymentMethodData();
        call.resolve(result);
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
        call.setKeepAlive(true);
        implementation.confirmWithCustomerDefaultPaymentMethod(new HyperswitchImpl.PaymentResultCallback() {
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
        call.setKeepAlive(true);
        implementation.confirmWithCustomerLastUsedPaymentMethod(new HyperswitchImpl.PaymentResultCallback() {
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
        // Event listeners are not yet bridged; resolve immediately so callers don't hang.
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
