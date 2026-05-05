package io.hyperswitch.capacitor;

import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.hyperswitch.CvcWidgetEvents;
import io.hyperswitch.PaymentEvents;
import io.hyperswitch.model.CustomEndpointConfiguration;
import io.hyperswitch.model.ElementsUpdateResult;
import io.hyperswitch.model.HyperswitchConfiguration;
import io.hyperswitch.model.HyperswitchEnvironment;
import io.hyperswitch.model.PaymentSessionConfiguration;
import io.hyperswitch.paymentsession.PaymentSessionHandler;
import io.hyperswitch.paymentsheet.PaymentResult;
import io.hyperswitch.sdk.Elements;
import io.hyperswitch.sdk.Hyperswitch;
import io.hyperswitch.utils.ConversionUtils;
import io.hyperswitch.view.CVCWidget;
import io.hyperswitch.view.PaymentElement;
import io.hyperswitch.sdk.HyperswitchBoundElement;
import io.hyperswitch.sdk.HyperswitchInstance;
import io.hyperswitch.sdk.PaymentSession;
import kotlin.Result;
import kotlin.Unit;
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
    private final Map<String, PaymentSessionHandler> handlerRegistry = new HashMap<>();
    private HyperswitchBoundElement paymentElementBound;
    private HyperswitchBoundElement cvcWidgetBound;

    // SDK view references (set by PaymentElementPlugin / CVCWidgetPlugin)
    private PaymentElement paymentElementView;
    private CVCWidget cvcWidgetView;

    // Legacy PaymentSession (used by presentPaymentSheet)
    private PaymentSession paymentSession;
    private String sdkAuth;

    // Event forwarding (set by HyperswitchPlugin via setEventListener)
    private NativeEventListener eventListener;

    /** Called by HyperswitchPlugin.load() to receive widget events for notifyListeners. */
    public void setEventListener(NativeEventListener listener) {
        this.eventListener = listener;
    }

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
        void onReady(String handlerId);
        void onError(String message);
    }

    public interface InitPaymentSessionCallback {
        void onReady();
        void onError(String message);
    }

    public interface CustomerSavedPaymentMethodsCallback {
        void onReady(String handlerId);
        void onError(String message);
    }

    /** Called when a payment widget emits a native event (e.g. CVC_STATUS, FORM_STATUS). */
    public interface NativeEventListener {
        void onEvent(String type, Map<String, Object> payload, String source);
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

        String customBackendUrl = customEndpointConfig != null ?
                customEndpointConfig.getOverrideCustomBackendEndpoint() != null ?
                        customEndpointConfig.getOverrideCustomBackendEndpoint() : customEndpointConfig.getCustomEndpoint() : null;

        String customLoggingUrl = customEndpointConfig != null ?
                customEndpointConfig.getOverrideCustomLoggingEndpoint() != null ?
                        customEndpointConfig.getOverrideCustomLoggingEndpoint() : customEndpointConfig.getCustomEndpoint() : null;

        if(customBackendUrl != null && customLoggingUrl != null) {
            paymentSession = new PaymentSession(
                    activity,
                    publishableKey,
                    customBackendUrl,
                    customLoggingUrl
            );
        } else {
            paymentSession = new PaymentSession(
                    activity,
                    publishableKey
            );
        }

        hyperswitchInstance = Hyperswitch.INSTANCE.init(
                activity,
                new HyperswitchConfiguration(publishableKey, profileId, customEndpointConfig, env)
        );
    }

    // ── View Registration (called by PaymentElementPlugin / CVCWidgetPlugin) ─────────────────

    public void registerPaymentElementView(PaymentElement view) {
        this.paymentElementView = view;
    }

    public void registerCVCWidgetView(CVCWidget view) {
        this.cvcWidgetView = view;
    }

    // ── Elements API ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates an Elements session for the given sdkAuthorization.
     * Mirrors: elements = hyperswitchInstance.elements(sessionConfig)
     */
    public void elements(JSObject elementsOptions, ElementsCallback callback) {
        Logger.info("Hyperswitch", "elements called");

        if (paymentSession == null) {
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

        sdkAuth = sdkAuthorization;
        // Create Elements session using the callback-based overload
        paymentSession.initPaymentSession(sdkAuthorization);

        PaymentSessionConfiguration sessionConfig = new PaymentSessionConfiguration(sdkAuthorization);

        // Create Elements session using the callback-based overload
        hyperswitchInstance.elements(sessionConfig, elementsInstance -> {
            this.elements = elementsInstance;
            if (callback != null) callback.onReady("");
            return null;
        });
    }

    /**
     * Binds a native view to the Elements session.
     * Mirrors:
     *   paymentElementBound = elements.bind(paymentElement, buildConfiguration()) { on(...) { } }
     *   cvcWidgetBound      = elements.bind(cvcWidget, configMap) { on(CvcWidgetEvents.CvcStatus) { } }
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
                Map<String, Object> configMap = jsObjectToMap(createOptions);
                List<String> subscribedEventsList = extractAndRemoveSubscribedEvents(configMap);
                paymentElementBound = elements.bind(
                        paymentElementView,
                        configMap,
                        builder -> {
                            if (subscribedEventsList.contains("FORM_STATUS")) {
                                builder.on(PaymentEvents.FormStatus.INSTANCE, event -> {
                                    fireEvent("FORM_STATUS", event.getPayload(), "paymentElement");
                                    return Unit.INSTANCE;
                                });
                            }
                            if (subscribedEventsList.contains("PAYMENT_METHOD_STATUS")) {
                                builder.on(PaymentEvents.PaymentMethodStatus.INSTANCE, event -> {
                                    fireEvent("PAYMENT_METHOD_STATUS", event.getPayload(), "paymentElement");
                                    return Unit.INSTANCE;
                                });
                            }
                            if (subscribedEventsList.contains("PAYMENT_METHOD_INFO_CARD")) {
                                builder.on(PaymentEvents.PaymentMethodInfoCard.INSTANCE, event -> {
                                    fireEvent("PAYMENT_METHOD_INFO_CARD", event.getPayload(), "paymentElement");
                                    return Unit.INSTANCE;
                                });
                            }
                            if (subscribedEventsList.contains("PAYMENT_METHOD_INFO_BILLING_ADDRESS")) {
                                builder.on(PaymentEvents.PaymentMethodInfoBillingAddress.INSTANCE, event -> {
                                    fireEvent("PAYMENT_METHOD_INFO_BILLING_ADDRESS", event.getPayload(), "paymentElement");
                                    return Unit.INSTANCE;
                                });
                            }
                            return Unit.INSTANCE;
                        }
                );
                Logger.info("Hyperswitch", "PaymentElement bound with configuration Map");

            } else if ("cvcWidget".equalsIgnoreCase(type) || "cvc".equalsIgnoreCase(type)) {
                if (cvcWidgetView == null) {
                    Logger.error("Hyperswitch", new Throwable("CvcWidgetView not registered yet"));
                    return;
                }
                Map<String, Object> configMap = jsObjectToMap(createOptions);
                cvcWidgetBound = elements.bind(
                        cvcWidgetView,
                        configMap,
                        builder -> {
                            builder.on(CvcWidgetEvents.CvcStatus.INSTANCE, event -> {
                                fireEvent("CVC_STATUS", event.getPayload(), "cvcWidget");
                                return Unit.INSTANCE;
                            });
                            return Unit.INSTANCE;
                        }
                );
                Logger.info("Hyperswitch", "CVCWidget bound with configuration Map");
            }
        });
    }

    /** Forwards a native widget event to JS via the registered NativeEventListener. */
    private void fireEvent(String type, Map<String, Object> payload, String source) {
        if (eventListener != null) {
            eventListener.onEvent(type, payload != null ? payload : new HashMap<>(), source);
        }
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

        sdkAuth = sdkAuthorization;

        // Callback overload: updateIntent(completion, onResult)
        // The completion lambda is called by the SDK to fetch the new config — we return it
        // synchronously since the JS side already resolved the new sdkAuthorization before
        // calling this plugin method.
        elements.updateIntent(
                continuation -> {
                    // Synchronous completion: resume the continuation with the new config.
                    // Kotlin coroutine runtime will see a non-COROUTINE_SUSPENDED return
                    // and treat this as an already-complete suspend function.
                    return newConfig;
                },
                result -> {
                    Logger.info("Hyperswitch", "updateIntent result: " + result);
                    if (callback != null) {
                        JSObject js = new JSObject();
                        switch (result) {
                            case ElementsUpdateResult.Success success ->
                                    js.put("type", "completed");
                            case ElementsUpdateResult.TotalFailure totalFailure -> {
                                Throwable cause = totalFailure.getCause();
                                js.put("type", "failure");
                                js.put("message", cause.getMessage());
                            }
                            case ElementsUpdateResult.PartialFailure partial -> {
                                js.put("type", "failure");
                                js.put("failedCount", partial.getFailed().size());
                                js.put("succeededCount", partial.getSucceeded().size());
                            }
                            case null, default -> js.put("type", "failure");
                        }
                        callback.onResult(js);
                    }
                    return null;
                }
        );
    }

    // ── InitPaymentSession (legacy – kept for presentPaymentSheet) ────────────────────────────

    public void initPaymentSession(JSObject paymentSessionOptions, InitPaymentSessionCallback callback) {
        Logger.info("Hyperswitch", "initPaymentSession called");

        if (paymentSession == null) {
            if (callback != null) callback.onError("Hyperswitch not initialised — call init() first");
            return;
        }

        String sdkAuthorization = paymentSessionOptions != null
                ? paymentSessionOptions.getString("sdkAuthorization")
                : null;

        sdkAuth = sdkAuthorization;

        paymentSession.initPaymentSession(sdkAuthorization != null ? sdkAuthorization : "");
        
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

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("sdkAuthorization", sdkAuth);
        configMap.put("publishableKey", paymentSession.getPublishableKey());
        configMap.put("from", "rn");
        configMap.put("type", "payment");

        if (sheetOptions != null) {
            Map<String, Object> configurationMap = jsObjectToMap(sheetOptions);
            configurationMap.remove("subscribedEvents");
            if (!configurationMap.isEmpty()) {
                configMap.put("configuration", configurationMap);
            }
        }

        paymentSession.presentPaymentSheet(configMap, null, paymentResult -> {
            try {
                JSObject jsResult = new JSObject();
                if (paymentResult instanceof PaymentResult.Completed) {
                    jsResult.put("type", "completed");
                    jsResult.put("message", ((PaymentResult.Completed) paymentResult).getData());
                    callback.onResult(jsResult);
                } else if (paymentResult instanceof PaymentResult.Canceled) {
                    jsResult.put("type", "canceled");
                    jsResult.put("message", ((PaymentResult.Canceled) paymentResult).getData());
                    callback.onResult(jsResult);
                } else if (paymentResult instanceof PaymentResult.Failed) {
                    String msg = ((PaymentResult.Failed) paymentResult).getThrowable().getMessage();
                    if (msg == null) msg = "Payment failed";
                    jsResult.put("type", "failed");
                    jsResult.put("message", msg);
                    callback.onResult(jsResult);
                } else {
                    jsResult.put("type", "failed");
                    jsResult.put("message", "Unknown status");
                    callback.onResult(jsResult);
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

    /**
     * Flow 1 (InitPaymentSession): asynchronously fetches the PaymentSessionHandler
     * from the active paymentSession, stores it in the registry, and returns its ID.
     */
    public void getCustomerSavedPaymentMethods(CustomerSavedPaymentMethodsCallback callback) {
        Logger.info("Hyperswitch", "getCustomerSavedPaymentMethods called");

        if (paymentSession == null) {
            if (callback != null) callback.onError("paymentSession not ready — call initPaymentSession() first");
            return;
        }

        paymentSession.getCustomerSavedPaymentMethods(handler -> {
            String handlerId = UUID.randomUUID().toString();
            handlerRegistry.put(handlerId, handler);
            Logger.info("Hyperswitch", "getCustomerSavedPaymentMethods ready, id: " + handlerId);
            if (callback != null) callback.onReady(handlerId);
            return null;
        });
    }

    public JSObject getCustomerSavedPaymentMethodData(String handlerId) {
        Logger.info("Hyperswitch", "getCustomerSavedPaymentMethodData called, id=" + handlerId);
        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            Logger.warn("Hyperswitch", "No handler for id: " + handlerId);
            return new JSObject();
        }
        JSObject result = new JSObject();
        try {
            Method method = handler.getClass().getMethod("getCustomerSavedPaymentMethodData-d1pmJ48");
            Object data = method.invoke(handler);
            if (data != null) result.put("data", data.toString());
        } catch (Exception e) {
            Logger.error("Hyperswitch", "getCustomerSavedPaymentMethodData failed", e);
        }
        return result;
    }

    public JSObject getCustomerDefaultSavedPaymentMethodData(String handlerId) {
        Logger.info("Hyperswitch", "getCustomerDefaultSavedPaymentMethodData called, id=" + handlerId);
        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            Logger.warn("Hyperswitch", "No handler for id: " + handlerId);
            return new JSObject();
        }
        JSObject result = new JSObject();
        try {
            Method method = handler.getClass().getMethod("getCustomerDefaultSavedPaymentMethodData-d1pmJ48");
            Object data = method.invoke(handler);
            if (data != null) result.put("data", data.toString());
        } catch (Exception e) {
            Logger.error("Hyperswitch", "getCustomerDefaultSavedPaymentMethodData failed", e);
        }
        return result;
    }

    public JSObject getCustomerLastUsedPaymentMethodData(String handlerId) {
        Logger.info("Hyperswitch", "getCustomerLastUsedPaymentMethodData called, id=" + handlerId);
        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            Logger.warn("Hyperswitch", "No handler for id: " + handlerId);
            return new JSObject();
        }
        JSObject result = new JSObject();
        try {
            Method method = handler.getClass().getMethod("getCustomerLastUsedPaymentMethodData-d1pmJ48");
            Object data = method.invoke(handler);
            if (data != null) result.put("data", data.toString());
        } catch (Exception e) {
            Logger.error("Hyperswitch", "getCustomerLastUsedPaymentMethodData failed", e);
        }
        return result;
    }

    public void confirmWithCustomerDefaultPaymentMethod(String handlerId, PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "confirmWithCustomerDefaultPaymentMethod called, id=" + handlerId);
        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            if (callback != null) callback.onError("No handler for id: " + handlerId);
            return;
        }
        activity.runOnUiThread(() ->
            handler.confirmWithCustomerDefaultPaymentMethod(cvcWidgetView, resumeWithPaymentResult(callback))
        );
    }

    public void confirmWithCustomerLastUsedPaymentMethod(String handlerId, PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "confirmWithCustomerLastUsedPaymentMethod called, id=" + handlerId);
        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            if (callback != null) callback.onError("No handler for id: " + handlerId);
            return;
        }
        activity.runOnUiThread(() ->
            handler.confirmWithCustomerLastUsedPaymentMethod(cvcWidgetView, resumeWithPaymentResult(callback))
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

    private Map<String, Object> jsObjectToMap(JSObject obj) {
        try {
            return new HashMap<>(ConversionUtils.readableMapToMap(ConversionUtils.convertJsonToMap(obj)));
        } catch (Exception e) {
            Logger.error("Hyperswitch", "Error converting JSObject to Map", e);
            return new HashMap<>();
        }
    }

    private List<String> extractAndRemoveSubscribedEvents(Map<String, Object> map) {
        List<String> result = new ArrayList<>();
        Object raw = map.remove("subscribedEvents");
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (item instanceof String) result.add((String) item);
            }
        }
        return result;
    }
}