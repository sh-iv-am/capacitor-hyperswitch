package io.hyperswitch.capacitor;

import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.hyperswitch.CvcWidgetEvents;
import io.hyperswitch.PaymentEventSubscriptionBuilder;
import io.hyperswitch.PaymentEvents;
import io.hyperswitch.model.CustomEndpointConfiguration;
import io.hyperswitch.model.ElementsUpdateResult;
import io.hyperswitch.model.HyperswitchConfiguration;
import io.hyperswitch.model.HyperswitchEnvironment;
import io.hyperswitch.model.PaymentSessionConfiguration;
import io.hyperswitch.paymentsession.PaymentSessionHandler;
import io.hyperswitch.paymentsheet.PaymentResult;
import io.hyperswitch.paymentsheet.PaymentSheet;
import io.hyperswitch.sdk.Elements;
import io.hyperswitch.sdk.Hyperswitch;
import io.hyperswitch.view.CVCWidget;
import io.hyperswitch.view.HyperswitchElement;
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

    // SDK view references (set by PaymentElementPlugin / CvcWidgetPlugin)
    private PaymentElement paymentElementView;
    private CVCWidget cvcWidgetView;

    // Legacy PaymentSession (used by presentPaymentSheet)
    private PaymentSession paymentSession;

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
        void onEvent(String type, Map<String, Object> payload);
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
                String handlerId = UUID.randomUUID().toString();
                handlerRegistry.put(handlerId, handler);
                Logger.info("Hyperswitch", "Elements ready, PaymentSessionHandler stored with id: " + handlerId);
                if (callback != null) callback.onReady(handlerId);
                return null;
            });

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
                // Build PaymentElement configuration Map from createOptions
                Map<String, Object> configMap = buildPaymentElementConfiguration(createOptions);
                paymentElementBound = elements.bind(
                        paymentElementView,
                        configMap,
                        builder -> {
                            // Subscribe to PaymentElement events
                            builder.on(PaymentEvents.FormStatus.INSTANCE, event -> {
                                fireEvent("FORM_STATUS", event.getPayload());
                                return Unit.INSTANCE;
                            });
                            builder.on(PaymentEvents.PaymentMethodStatus.INSTANCE, event -> {
                                fireEvent("PAYMENT_METHOD_STATUS", event.getPayload());
                                return Unit.INSTANCE;
                            });
                            builder.on(PaymentEvents.PaymentMethodInfoCard.INSTANCE, event -> {
                                fireEvent("PAYMENT_METHOD_INFO_CARD", event.getPayload());
                                return Unit.INSTANCE;
                            });
                            builder.on(PaymentEvents.PaymentMethodInfoBillingAddress.INSTANCE, event -> {
                                fireEvent("PAYMENT_METHOD_INFO_BILLING_ADDRESS", event.getPayload());
                                return Unit.INSTANCE;
                            });
                            return Unit.INSTANCE;
                        }
                );
                Logger.info("Hyperswitch", "PaymentElement bound with configuration Map");

            } else if ("cvcWidget".equalsIgnoreCase(type) || "cvc".equalsIgnoreCase(type)) {
                if (cvcWidgetView == null) {
                    Logger.error("Hyperswitch", new Throwable("CvcWidgetView not registered yet"));
                    return;
                }
                // Build CVC widget configuration Map from createOptions
                // Pass directly to elements.bind() - SDK should handle conversion
                Map<String, Object> configMap = buildCvcWidgetMap(createOptions);
                // Mirrors: elements.bind(cvcWidget, configMap) { on(CvcWidgetEvents.CvcStatus) { println(it) } }
                cvcWidgetBound = elements.bind(
                        cvcWidgetView,
                        configMap,
                        builder -> {
                            builder.on(CvcWidgetEvents.CvcStatus.INSTANCE, event -> {
                                fireEvent("CVC_STATUS", event.getPayload());
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
    private void fireEvent(String type, Map<String, Object> payload) {
        if (eventListener != null) {
            eventListener.onEvent(type, payload != null ? payload : new HashMap<>());
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

        // Build configuration Map from sheetOptions (matches React Native pattern)
        Map<String, Object> configMap = buildPaymentSheetConfiguration(sheetOptions);

        // For now, use the existing presentPaymentSheet with Configuration object
        // SDK doesn't have a Map-based overload for PaymentSession yet
        PaymentSheet.Configuration configuration = new PaymentSheet.Configuration("Merchant");
        paymentSession.presentPaymentSheet(configuration, null, paymentResult -> {
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

    // ── CVC Widget Configuration Builder ─────────────────────────────────────────────────────────

    /**
     * Builds CVC widget configuration Map from JS createOptions.
     * Simple passthrough - SDK should handle the Map properly.
     */
    private Map<String, Object> buildCvcWidgetMap(JSObject createOptions) {
        Map<String, Object> config = new HashMap<>();
        
        if (createOptions == null) {
            // Always add subscribedEvents for CVC widget (must be Array, not List)
            config.put("subscribedEvents", new String[]{"CVC_STATUS"});
            return config;
        }
        
        // Extract appearance - passthrough as Map
        JSObject appearanceObj = createOptions.getJSObject("appearance");
        if (appearanceObj != null) {
            Map<String, Object> appearanceMap = convertJsObjectToMap(appearanceObj);
            if (!appearanceMap.isEmpty()) {
                config.put("appearance", appearanceMap);
            }
        }
        
        // Placeholder (mapped to cvv field structure)
        String placeholder = createOptions.getString("placeholder");
        if (placeholder != null && !placeholder.isEmpty()) {
            Map<String, Object> placeholderMap = new HashMap<>();
            placeholderMap.put("cvv", placeholder);
            config.put("placeholder", placeholderMap);
        }
        
        // SDK Authorization (if provided)
        String sdkAuth = createOptions.getString("sdkAuthorization");
        if (sdkAuth != null && !sdkAuth.isEmpty()) {
            config.put("sdkAuthorization", sdkAuth);
        }
        
        // Always subscribe to CVC_STATUS events (required for CVC widget, must be Array not List)
        config.put("subscribedEvents", new String[]{"CVC_STATUS"});
        
        return config;
    }
    
    /**
     * Builds PaymentSheet configuration Map from JS sheetOptions (for presentPaymentSheet).
     * Matches React Native PaymentSheetOptions structure.
     */
    private Map<String, Object> buildPaymentSheetConfiguration(JSObject sheetOptions) {
        Map<String, Object> config = new HashMap<>();
        
        if (sheetOptions == null) {
            return config;
        }
        
        // Extract appearance - passthrough as Map
        JSObject appearanceObj = sheetOptions.getJSObject("appearance");
        if (appearanceObj != null) {
            Map<String, Object> appearanceMap = convertJsObjectToMap(appearanceObj);
            if (!appearanceMap.isEmpty()) {
                config.put("appearance", appearanceMap);
            }
        }
        
        // Placeholder
        JSObject placeholderObj = sheetOptions.getJSObject("placeholder");
        if (placeholderObj != null) {
            Map<String, Object> placeholderMap = convertJsObjectToMap(placeholderObj);
            if (!placeholderMap.isEmpty()) {
                config.put("placeholder", placeholderMap);
            }
        }
        
        // Text labels
        String primaryButtonLabel = sheetOptions.getString("primaryButtonLabel");
        if (primaryButtonLabel != null) {
            config.put("primaryButtonLabel", primaryButtonLabel);
        }
        
        String paymentSheetHeaderLabel = sheetOptions.getString("paymentSheetHeaderLabel");
        if (paymentSheetHeaderLabel != null) {
            config.put("paymentSheetHeaderLabel", paymentSheetHeaderLabel);
        }
        
        String savedPaymentSheetHeaderLabel = sheetOptions.getString("savedPaymentSheetHeaderLabel");
        if (savedPaymentSheetHeaderLabel != null) {
            config.put("savedPaymentSheetHeaderLabel", savedPaymentSheetHeaderLabel);
        }
        
        String merchantDisplayName = sheetOptions.getString("merchantDisplayName");
        if (merchantDisplayName != null) {
            config.put("merchantDisplayName", merchantDisplayName);
        }
        
        // Payment flow options
        if (sheetOptions.has("allowsDelayedPaymentMethods")) {
            config.put("allowsDelayedPaymentMethods", sheetOptions.optBoolean("allowsDelayedPaymentMethods", false));
        }
        
        if (sheetOptions.has("allowsPaymentMethodsRequiringShippingAddress")) {
            config.put("allowsPaymentMethodsRequiringShippingAddress", sheetOptions.optBoolean("allowsPaymentMethodsRequiringShippingAddress", false));
        }
        
        // Display options
        if (sheetOptions.has("displaySavedPaymentMethods")) {
            config.put("displaySavedPaymentMethods", sheetOptions.optBoolean("displaySavedPaymentMethods", true));
        }
        
        if (sheetOptions.has("displaySavedPaymentMethodsCheckbox")) {
            config.put("displaySavedPaymentMethodsCheckbox", sheetOptions.optBoolean("displaySavedPaymentMethodsCheckbox", true));
        }
        
        if (sheetOptions.has("displayDefaultSavedPaymentIcon")) {
            config.put("displayDefaultSavedPaymentIcon", sheetOptions.optBoolean("displayDefaultSavedPaymentIcon", true));
        }
        
        // Feature flags
        if (sheetOptions.has("disableBranding")) {
            config.put("disableBranding", sheetOptions.optBoolean("disableBranding", false));
        }
        
        if (sheetOptions.has("defaultView")) {
            config.put("defaultView", sheetOptions.optBoolean("defaultView", false));
        }
        
        if (sheetOptions.has("hideConfirmButton")) {
            config.put("hideConfirmButton", sheetOptions.optBoolean("hideConfirmButton", false));
        }
        
        return config;
    }
    
    /**
     * Builds PaymentElement configuration Map from JS createOptions.
     * Similar to PaymentSheetOptions but for Elements API.
     */
    private Map<String, Object> buildPaymentElementConfiguration(JSObject createOptions) {
        Map<String, Object> config = new HashMap<>();

        if (createOptions == null) return config;

        JSObject appearanceObj = createOptions.getJSObject("appearance");
        if (appearanceObj != null) {
            Map<String, Object> appearanceMap = convertJsObjectToMap(appearanceObj);
            if (!appearanceMap.isEmpty()) {
                config.put("appearance", appearanceMap);
            }
        }

        JSObject placeholderObj = createOptions.getJSObject("placeholder");
        if (placeholderObj != null) {
            Map<String, Object> placeholderMap = convertJsObjectToMap(placeholderObj);
            if (!placeholderMap.isEmpty()) {
                config.put("placeholder", placeholderMap);
            }
        }
        
        // Essential fields - REQUIRED for PaymentElement to render
        String merchantDisplayName = createOptions.getString("merchantDisplayName");
        if (merchantDisplayName != null && !merchantDisplayName.isEmpty()) {
            config.put("merchantDisplayName", merchantDisplayName);
        } else {
            // Provide default to prevent silent close
            config.put("merchantDisplayName", "Merchant");
        }
        
        // Optional label fields
        String primaryButtonLabel = createOptions.getString("primaryButtonLabel");
        if (primaryButtonLabel != null && !primaryButtonLabel.isEmpty()) {
            config.put("primaryButtonLabel", primaryButtonLabel);
        }
        
        String paymentSheetHeaderLabel = createOptions.getString("paymentSheetHeaderLabel");
        if (paymentSheetHeaderLabel != null && !paymentSheetHeaderLabel.isEmpty()) {
            config.put("paymentSheetHeaderLabel", paymentSheetHeaderLabel);
        }
        
        String savedPaymentSheetHeaderLabel = createOptions.getString("savedPaymentSheetHeaderLabel");
        if (savedPaymentSheetHeaderLabel != null && !savedPaymentSheetHeaderLabel.isEmpty()) {
            config.put("savedPaymentSheetHeaderLabel", savedPaymentSheetHeaderLabel);
        }
        
        // Payment flow options
        if (createOptions.has("allowsDelayedPaymentMethods")) {
            config.put("allowsDelayedPaymentMethods", createOptions.optBoolean("allowsDelayedPaymentMethods", false));
        }
        
        if (createOptions.has("allowsPaymentMethodsRequiringShippingAddress")) {
            config.put("allowsPaymentMethodsRequiringShippingAddress", createOptions.optBoolean("allowsPaymentMethodsRequiringShippingAddress", false));
        }
        
        // Display options
        if (createOptions.has("displaySavedPaymentMethods")) {
            config.put("displaySavedPaymentMethods", createOptions.optBoolean("displaySavedPaymentMethods", true));
        }
        
        if (createOptions.has("displaySavedPaymentMethodsCheckbox")) {
            config.put("displaySavedPaymentMethodsCheckbox", createOptions.optBoolean("displaySavedPaymentMethodsCheckbox", true));
        }
        
        if (createOptions.has("displayDefaultSavedPaymentIcon")) {
            config.put("displayDefaultSavedPaymentIcon", createOptions.optBoolean("displayDefaultSavedPaymentIcon", true));
        }
        
        // Feature flags
        if (createOptions.has("disableBranding")) {
            config.put("disableBranding", createOptions.optBoolean("disableBranding", false));
        }
        
        if (createOptions.has("defaultView")) {
            config.put("defaultView", createOptions.optBoolean("defaultView", false));
        }
        
        if (createOptions.has("hideConfirmButton")) {
            config.put("hideConfirmButton", createOptions.optBoolean("hideConfirmButton", false));
        }
        
        config.put("subscribedEvents", new String[]{
            "FORM_STATUS",
            "PAYMENT_METHOD_STATUS",
            "PAYMENT_METHOD_INFO_CARD",
            "PAYMENT_METHOD_INFO_BILLING_ADDRESS"
        });

        return config;
    }
    
    private Map<String, Object> convertJsObjectToMap(JSObject obj) {
        return convertJsObjectToMapInternal(obj, false);
    }

    private Map<String, Object> convertJsObjectToMapInternal(JSONObject obj, boolean isAppearanceContext) {
        Map<String, Object> map = new HashMap<>();
        if (obj == null) return map;

        try {
            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String key = it.next();
                Object value = obj.opt(key);

                if (value instanceof JSONObject) {
                    boolean childIsAppearance = isAppearanceContext ||
                        key.equals("appearance") || key.equals("colors") ||
                        key.equals("shapes") || key.equals("font") ||
                        key.equals("light") || key.equals("dark");
                    map.put(key, convertJsObjectToMapInternal((JSONObject) value, childIsAppearance));
                } else if (value instanceof JSONArray) {
                    JSONArray arr = (JSONArray) value;
                    List<Object> list = new ArrayList<>(arr.length());
                    for (int i = 0; i < arr.length(); i++) {
                        Object item = arr.opt(i);
                        if (item instanceof JSONObject) {
                            list.add(convertJsObjectToMapInternal((JSONObject) item, isAppearanceContext));
                        } else if (item instanceof Double && isAppearanceNumericKey(key)) {
                            list.add(((Double) item).floatValue());
                        } else {
                            list.add(item);
                        }
                    }
                    map.put(key, list);
                } else if (value instanceof Double && isAppearanceNumericKey(key)) {
                    map.put(key, ((Double) value).floatValue());
                } else if (value != null) {
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            Logger.error("Hyperswitch", "Error converting JSONObject to Map", e);
        }

        return map;
    }
    
    /**
     * Checks if a key is a numeric field in appearance that needs Float conversion
     */
    private boolean isAppearanceNumericKey(String key) {
        return key.equals("borderRadius") || 
               key.equals("borderWidth") || 
               key.equals("scale") ||
               key.equals("cornerRadius") ||
               key.equals("x") || 
               key.equals("y") ||
               key.equals("opacity") ||
               key.equals("blurRadius") ||
               key.equals("intensity");
    }
}