package io.hyperswitch.capacitor;

import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.hyperswitch.model.CustomEndpointConfiguration;
import io.hyperswitch.model.HyperswitchConfiguration;
import io.hyperswitch.model.HyperswitchEnvironment;
import io.hyperswitch.model.PaymentSessionConfiguration;
import io.hyperswitch.paymentsession.PaymentSessionHandler;
import io.hyperswitch.paymentsheet.PaymentResult;
import io.hyperswitch.paymentsheet.PaymentSheet;
import io.hyperswitch.sdk.Elements;
import io.hyperswitch.sdk.Hyperswitch;
import io.hyperswitch.view.CVCWidget;
import io.hyperswitch.view.PaymentElement;
import io.hyperswitch.sdk.HyperswitchBoundElement;
import io.hyperswitch.sdk.HyperswitchInstance;
import io.hyperswitch.sdk.PaymentSession;
import kotlin.Result;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class HyperswitchImpl {


    // ── Singleton ──────────────────────────────────────────────────────────────────────────────

    private static HyperswitchImpl instance;

    public static HyperswitchImpl getInstance() {
        if (instance == null) {
            instance = new HyperswitchImpl();
        }
        return instance;
    }

    // ── State ──────────────────────────────────────────────────────────────────────────────────

    private AppCompatActivity activity;
    private HyperswitchInstance hyperswitchInstance;

    // Elements API state
    private Elements elements;
    private PaymentSessionHandler paymentSessionHandler;
    private HyperswitchBoundElement paymentElementBound;
    private HyperswitchBoundElement cvcWidgetBound;

    // SDK view references (set by PaymentElementPlugin / CvcWidgetPlugin)
    private PaymentElement paymentElementView;
    private CVCWidget cvcWidgetView;

    // Legacy PaymentSession (used by presentPaymentSheet)
    private PaymentSession paymentSession;

    // ── Callbacks ──────────────────────────────────────────────────────────────────────────────

    public interface PaymentSheetCallback {
        void onResult(JSObject result);
        void onError(Exception e);
    }

    public interface PaymentResultCallback {
        void onResult(JSObject result);
        void onError(String message);
    }

    public interface ElementsCallback {
        void onReady();
        void onError(String message);
    }

    public interface InitPaymentSessionCallback {
        void onReady();
        void onError(String message);
    }

    // ── Init ───────────────────────────────────────────────────────────────────────────────────

    public void init(AppCompatActivity activity, String publishableKey, String profileId,
                     JSObject customConfig, String environment) {
        this.activity = activity;

        CustomEndpointConfiguration customEndpointConfig = null;
        if (customConfig != null) {
            customEndpointConfig = new CustomEndpointConfiguration(
                    customConfig.getString("customEndpoint"),
                    customConfig.getString("overrideCustomBackendEndpoint"),
                    customConfig.getString("overrideCustomAssetsEndpoint"),
                    customConfig.getString("overrideCustomSDKConfigEndpoint"),
                    customConfig.getString("overrideCustomConfirmEndpoint"),
                    customConfig.getString("overrideCustomAirborneEndpoint"),
                    customConfig.getString("overrideCustomLoggingEndpoint")
            );
        }

        HyperswitchEnvironment env = HyperswitchEnvironment.PROD;
        if ("SANDBOX".equals(environment)) {
            env = HyperswitchEnvironment.SANDBOX;
        } else if ("INTEG".equals(environment)) {
            env = HyperswitchEnvironment.INTEG;
        }

        Logger.info("Hyperswitch", "Initialized with publishableKey: " + publishableKey);

        hyperswitchInstance = Hyperswitch.INSTANCE.init(
                activity,
                new HyperswitchConfiguration(publishableKey, profileId, customEndpointConfig, env)
        );
    }

    // ── View Registration (called by PaymentElementPlugin / CvcWidgetPlugin) ─────────────────

    public void registerPaymentElementView(PaymentElement view) {
        this.paymentElementView = view;
    }

    public void registerCvcWidgetView(CVCWidget view) {
        this.cvcWidgetView = view;
    }

    // ── Elements API ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates an Elements session for the given sdkAuthorization.
     * Mirrors: elements = hyperswitchInstance.elements(sessionConfig)
     */
    public void elements(JSObject elementsOptions, ElementsCallback callback) {
        Logger.info("Hyperswitch", "elements called");

        if (hyperswitchInstance == null) {
            if (callback != null) callback.onError("Hyperswitch not initialised — call init() first");
            return;
        }

        String sdkAuthorization = elementsOptions != null
                ? elementsOptions.getString("sdkAuthorization")
                : null;

        if (sdkAuthorization == null || sdkAuthorization.isEmpty()) {
            if (callback != null) callback.onError("sdkAuthorization is required");
            return;
        }

        PaymentSessionConfiguration sessionConfig = new PaymentSessionConfiguration(sdkAuthorization);

        // Create Elements session using the callback-based overload
        hyperswitchInstance.elements(sessionConfig, elementsInstance -> {
            this.elements = elementsInstance;

            // Fetch saved payment methods immediately so they are ready
            elementsInstance.getCustomerSavedPaymentMethods(handler -> {
                this.paymentSessionHandler = handler;
                Logger.info("Hyperswitch", "Elements ready, PaymentSessionHandler loaded");
                if (callback != null) callback.onReady();
                return null;
            });

            return null;
        });
    }

    /**
     * Binds a native view to the Elements session.
     * Mirrors: paymentElementBound = elements.bind(paymentElement, buildConfiguration())
     *          cvcWidgetBound      = elements.bind(cvcWidget)
     */
    public void createElement(String type, JSObject createOptions) {
        Logger.info("Hyperswitch", "createElement called with type: " + type);

        if (elements == null) {
            Logger.error("Hyperswitch", new Throwable("elements() must be called before createElement()"));
            return;
        }

        activity.runOnUiThread(() -> {
            if ("paymentElement".equalsIgnoreCase(type) || "payment".equalsIgnoreCase(type)) {
                if (paymentElementView == null) {
                    Logger.error("Hyperswitch", new Throwable("PaymentElementView not registered yet"));
                    return;
                }
                paymentElementBound = elements.bind(paymentElementView, new PaymentSheet.Configuration("abc"), null);
                Logger.info("Hyperswitch", "PaymentElement bound successfully");

            } else if ("cvcWidget".equalsIgnoreCase(type) || "cvc".equalsIgnoreCase(type)) {
                if (cvcWidgetView == null) {
                    Logger.error("Hyperswitch", new Throwable("CvcWidgetView not registered yet"));
                    return;
                }
                cvcWidgetBound = elements.bind(cvcWidgetView, null, null);
                Logger.info("Hyperswitch", "CVCWidget bound successfully");
            }
        });
    }

    // ── UpdateIntent ──────────────────────────────────────────────────────────────────────────

    /**
     * Updates the payment intent on all bound elements.
     * Mirrors: elements.updateIntent { fetchUpdatedAuthorization() }
     *
     * @param sdkAuthorization new sdkAuthorization from your backend
     * @param callback         result callback
     */
    public void updateIntent(String sdkAuthorization, PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "updateIntent called");

        if (elements == null) {
            if (callback != null) callback.onError("elements() must be called first");
            return;
        }

        PaymentSessionConfiguration newConfig = new PaymentSessionConfiguration(sdkAuthorization);

        // Callback overload: updateIntent(suspendSupplier, resultHandler)
        elements.updateIntent(
                continuation -> {
                    // This acts as the suspend supplier — just return the new config
                    return newConfig;
                },
                result -> {
                    Logger.info("Hyperswitch", "updateIntent result: " + result);
                    if (callback != null) {
                        JSObject js = new JSObject();
                        js.put("type", result.getClass().getSimpleName());
                        callback.onResult(js);
                    }
                    return null;
                }
        );
    }

    // ── InitPaymentSession (legacy – kept for presentPaymentSheet) ────────────────────────────

    public void initPaymentSession(JSObject paymentSessionOptions, InitPaymentSessionCallback callback) {
        Logger.info("Hyperswitch", "initPaymentSession called");

        if (hyperswitchInstance == null) {
            if (callback != null) callback.onError("Hyperswitch not initialised — call init() first");
            return;
        }

        String sdkAuthorization = paymentSessionOptions != null
                ? paymentSessionOptions.getString("sdkAuthorization")
                : null;

        hyperswitchInstance.initPaymentSession(
                new PaymentSessionConfiguration(sdkAuthorization != null ? sdkAuthorization : ""),
                session -> {
                    this.paymentSession = session;
                    Logger.info("Hyperswitch", "initPaymentSession ready");
                    if (callback != null) callback.onReady();
                    return null;
                }
        );
    }

    // ── PresentPaymentSheet (legacy) ──────────────────────────────────────────────────────────

    public void presentPaymentSheet(JSObject sheetOptions, PaymentSheetCallback callback) {
        Logger.info("Hyperswitch", "presentPaymentSheet called");

        if (paymentSession == null) {
            if (callback != null) callback.onError(new IllegalStateException("Payment session not initialised"));
            return;
        }

//        Map<String, Object> configuration = new HashMap<>();
//        configuration.put("merchantDisplayName", "abc");
//
//        Map<String, Object> configurationMap = new HashMap<>();
//        configurationMap.put("type", "payment");
//        configurationMap.put("configuration", configuration);

        paymentSession.launchPaymentSheet(new PaymentSheet.Configuration("abc"), paymentResult -> {
            try {
                JSObject jsResult = new JSObject();
                switch (paymentResult) {
                    case PaymentResult.Completed result -> {
                        jsResult.put("type", "completed");
                        jsResult.put("message", result.getData());
                        callback.onResult(jsResult);
                    }
                    case PaymentResult.Canceled result -> {
                        jsResult.put("type", "canceled");
                        jsResult.put("message", result.getData());
                        callback.onResult(jsResult);
                    }
                    case PaymentResult.Failed result -> {
                        String msg = result.getThrowable().getMessage() != null
                                ? result.getThrowable().getMessage()
                                : "Payment failed";
                        jsResult.put("type", "failed");
                        jsResult.put("message", msg);
                        callback.onResult(jsResult);
                    }
                    default -> {
                        jsResult.put("type", "failed");
                        jsResult.put("message", "Unknown status");
                        callback.onResult(jsResult);
                    }
                }
            } catch (Exception e) {
                Logger.error("Hyperswitch", "Error in presentPaymentSheet callback", e);
                callback.onError(e);
            }
            return null;
        });
    }

    // ── ConfirmPayment (via PaymentElement bound element) ─────────────────────────────────────

    /**
     * Confirms payment through the bound PaymentElement.
     * Mirrors: val result = paymentElementBound.confirmPayment()
     */
    public void confirmPayment(JSObject confirmParams, PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "confirmPayment called");

        if (paymentElementBound == null) {
            if (callback != null) callback.onError("PaymentElement not bound — call createElement() first");
            return;
        }

        activity.runOnUiThread(() ->
            paymentElementBound.confirmPayment(result -> {
                if (callback != null) callback.onResult(paymentResultToJSObject(result));
                return null;
            })
        );
    }

    // ── CustomerSavedPaymentMethods ───────────────────────────────────────────────────────────

    public JSObject getCustomerSavedPaymentMethods() {
        Logger.info("Hyperswitch", "getCustomerSavedPaymentMethods called");

        if (paymentSessionHandler == null) {
            Logger.warn("Hyperswitch", "paymentSessionHandler not ready");
            return new JSObject();
        }

        JSObject result = new JSObject();
        try {
            Method method = paymentSessionHandler.getClass()
                    .getMethod("getCustomerSavedPaymentMethodData-d1pmJ48");
            Object data = method.invoke(paymentSessionHandler);
            if (data != null) result.put("data", data.toString());
        } catch (Exception e) {
            Logger.error("Hyperswitch", "getCustomerSavedPaymentMethods failed", e);
        }
        return result;
    }

    /**
     * Returns the default saved payment method data.
     * Mirrors: paymentSessionHandler.getCustomerDefaultSavedPaymentMethodData()
     */
    public JSObject getCustomerDefaultSavedPaymentMethodData() {
        Logger.info("Hyperswitch", "getCustomerDefaultSavedPaymentMethodData called");

        if (paymentSessionHandler == null) {
            Logger.warn("Hyperswitch", "paymentSessionHandler not ready");
            return new JSObject();
        }

        // The method name is mangled by Kotlin (inline value class return type).
        // Must be invoked via reflection.
        JSObject result = new JSObject();
        try {
            Method method = paymentSessionHandler.getClass()
                    .getMethod("getCustomerDefaultSavedPaymentMethodData-d1pmJ48");
            Object data = method.invoke(paymentSessionHandler);
            if (data != null) result.put("data", data.toString());
        } catch (Exception e) {
            Logger.error("Hyperswitch", "getCustomerDefaultSavedPaymentMethodData failed", e);
        }
        return result;
    }

    /**
     * Returns the last-used saved payment method data.
     * The method name is mangled by Kotlin (inline value class return type) so it must
     * be called via reflection as `getCustomerLastUsedPaymentMethodData-d1pmJ48`.
     */
    public JSObject getCustomerLastUsedPaymentMethodData() {
        Logger.info("Hyperswitch", "getCustomerLastUsedPaymentMethodData called");

        if (paymentSessionHandler == null) {
            Logger.warn("Hyperswitch", "paymentSessionHandler not ready");
            return new JSObject();
        }

        JSObject result = new JSObject();
        try {
            Method method = paymentSessionHandler.getClass()
                    .getMethod("getCustomerLastUsedPaymentMethodData-d1pmJ48");
            Object data = method.invoke(paymentSessionHandler);
            if (data != null) result.put("data", data.toString());
        } catch (Exception e) {
            Logger.error("Hyperswitch", "getCustomerLastUsedPaymentMethodData failed", e);
        }
        return result;
    }

    /**
     * Confirm with the customer's default saved payment method (with optional CVC).
     * Must run on the main thread — the SDK touches views internally.
     */
    public void confirmWithCustomerDefaultPaymentMethod(PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "confirmWithCustomerDefaultPaymentMethod called");

        if (paymentSessionHandler == null) {
            if (callback != null) callback.onError("paymentSessionHandler not ready");
            return;
        }

        activity.runOnUiThread(() ->
            paymentSessionHandler.confirmWithCustomerDefaultPaymentMethod(
                    cvcWidgetView,
                    resumeWithPaymentResult(callback)
            )
        );
    }

    /**
     * Confirm with the customer's last-used payment method (with optional CVC).
     * Must run on the main thread — the SDK touches views internally.
     */
    public void confirmWithCustomerLastUsedPaymentMethod(PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "confirmWithCustomerLastUsedPaymentMethod called");

        if (paymentSessionHandler == null) {
            if (callback != null) callback.onError("paymentSessionHandler not ready");
            return;
        }

        activity.runOnUiThread(() ->
            paymentSessionHandler.confirmWithCustomerLastUsedPaymentMethod(
                    cvcWidgetView,
                    resumeWithPaymentResult(callback)
            )
        );
    }

    /** Builds a bare-minimum Continuation that forwards the suspend result to our callback. */
    private Continuation<PaymentResult> resumeWithPaymentResult(PaymentResultCallback callback) {
        return new Continuation<PaymentResult>() {
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(Object result) {
                if (callback == null) return;
                if (result instanceof Result.Failure) {
                    Throwable t = ((Result.Failure) result).exception;
                    callback.onError(t != null ? t.getMessage() : "Unknown error");
                } else if (result instanceof PaymentResult) {
                    callback.onResult(paymentResultToJSObject((PaymentResult) result));
                } else {
                    callback.onError("Unexpected result type: " + (result != null ? result.getClass().getName() : "null"));
                }
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private JSObject paymentResultToJSObject(PaymentResult result) {
        JSObject js = new JSObject();
        try {
            if (result instanceof PaymentResult.Completed) {
                js.put("type", "completed");
                js.put("message", ((PaymentResult.Completed) result).getData());
            } else if (result instanceof PaymentResult.Canceled) {
                js.put("type", "canceled");
                js.put("message", ((PaymentResult.Canceled) result).getData());
            } else if (result instanceof PaymentResult.Failed) {
                Throwable err = ((PaymentResult.Failed) result).getThrowable();
                js.put("type", "failed");
                js.put("message", err != null ? err.getMessage() : "Payment failed");
            } else {
                js.put("type", "failed");
                js.put("message", "Unknown result");
            }
        } catch (Exception e) {
            Logger.error("Hyperswitch", "Error mapping PaymentResult", e);
        }
        return js;
    }
}
