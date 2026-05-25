package io.hyperswitch.capacitor;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.hyperswitch.CvcWidgetEvents;
import io.hyperswitch.PaymentEvents;
import io.hyperswitch.model.CustomEndpointConfiguration;
import io.hyperswitch.model.OverrideEndpoints;
import io.hyperswitch.model.ElementsUpdateResult;
import io.hyperswitch.model.HyperswitchConfiguration;
import io.hyperswitch.model.HyperswitchEnvironment;
import io.hyperswitch.model.PaymentSessionConfiguration;
import io.hyperswitch.paymentsession.PaymentSessionHandler;
import io.hyperswitch.paymentsession.SavedPaymentMethodsConfiguration;
import io.hyperswitch.paymentsheet.PaymentResult;
import io.hyperswitch.sdk.Elements;
import io.hyperswitch.sdk.Hyperswitch;
import io.hyperswitch.sdk.HyperswitchBoundElement;
import io.hyperswitch.sdk.HyperswitchInstance;
import io.hyperswitch.sdk.PaymentSession;
import io.hyperswitch.utils.ConversionUtils;
import io.hyperswitch.view.CVCWidget;
import io.hyperswitch.view.PaymentElement;
import io.hyperswitch.view.PaymentResultListener;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class HyperswitchImpl {

    // ── Singleton ──────────────────────────────────────────────────────────────────────────────

    private static volatile HyperswitchImpl instance;

    public static HyperswitchImpl getInstance() {
        if (instance == null) {
            synchronized (HyperswitchImpl.class) {
                if (instance == null) {
                    instance = new HyperswitchImpl();
                }
            }
        }
        return instance;
    }

    // ── State ──────────────────────────────────────────────────────────────────────────────────

    private volatile AppCompatActivity activity;
    private volatile HyperswitchInstance hyperswitchInstance;

    // Elements API state
    private volatile Elements elements;
    private final Map<String, PaymentSessionHandler> handlerRegistry = new ConcurrentHashMap<>();
    private volatile HyperswitchBoundElement paymentElementBound;
    private volatile HyperswitchBoundElement cvcWidgetBound;

    // SDK view references (set by PaymentElementPlugin / CVCWidgetPlugin)
    private volatile PaymentElement paymentElementView;
    private volatile CVCWidget cvcWidgetView;

    // Legacy PaymentSession (used by presentPaymentSheet)
    private volatile PaymentSession paymentSession;
    private volatile String sdkAuth;
    private volatile String customBackendUrl;
    private volatile String customLoggingUrl;

    // Event forwarding (set by HyperswitchPlugin via setEventListener)
    private volatile NativeEventListener eventListener;
    private volatile AtomicBoolean pendingConfirmCallbackRegistration = new AtomicBoolean(false);

    /**
     * Called by HyperswitchPlugin.load() to receive widget events for notifyListeners.
     */
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

    /**
     * Called when a payment widget emits a native event (e.g. CVC_STATUS, FORM_STATUS).
     */
    public interface NativeEventListener {
        void onEvent(String type, Map<String, Object> payload, String source);
    }

    // ── Init ───────────────────────────────────────────────────────────────────────────────────

    public void init(AppCompatActivity activity, String publishableKey, String profileId,
                     JSObject customConfig, String environment) {
        this.activity = activity;

        CustomEndpointConfiguration customEndpointConfig = null;
        if (customConfig != null) {
            JSObject overrideObj = customConfig.getJSObject("overrideEndpoints");
            String commonEndpoint = customConfig.getString("commonEndpoint");
            if (overrideObj != null) {
                OverrideEndpoints overrideEndpoints = new OverrideEndpoints(
                        overrideObj.getString("customBackendEndpoint"),
                        overrideObj.getString("customLoggingEndpoint"),
                        overrideObj.getString("customAssetEndpoint"),
                        overrideObj.getString("customSDKConfigEndpoint"),
                        overrideObj.getString("customConfirmEndpoint"),
                        overrideObj.getString("customAirborneEndpoint")
                );
                customEndpointConfig = new CustomEndpointConfiguration(overrideEndpoints, null);
            } else if (commonEndpoint != null) {
                customEndpointConfig = new CustomEndpointConfiguration(null, commonEndpoint);
            }
        }

        HyperswitchEnvironment env = HyperswitchEnvironment.PROD;
        String envUpper = environment != null ? environment.toUpperCase() : "PRODUCTION";
        if ("SANDBOX".equals(envUpper)) {
            env = HyperswitchEnvironment.SANDBOX;
        } else if ("INTEG".equals(envUpper)) {
            env = HyperswitchEnvironment.INTEG;
        }

        Logger.info("Hyperswitch", "Initialized with publishableKey: " + publishableKey);

        if (customEndpointConfig != null) {
            OverrideEndpoints oe = customEndpointConfig.getOverrideEndpoints();
            String common = customEndpointConfig.getCommonEndpoint();
            customBackendUrl = (oe != null && oe.getCustomBackendEndpoint() != null)
                    ? oe.getCustomBackendEndpoint() : common;
            customLoggingUrl = (oe != null && oe.getCustomLoggingEndpoint() != null)
                    ? oe.getCustomLoggingEndpoint() : common;
        }

        hyperswitchInstance = Hyperswitch.INSTANCE.init(
                activity,
                new HyperswitchConfiguration(publishableKey, profileId, customEndpointConfig, env)
        );
    }

    // ── View Registration (called by PaymentElementPlugin / CVCWidgetPlugin) ─────────────────

    public void registerPaymentElementView(PaymentElement view) {
        if (view == null) {
            unbindPaymentElement();
        }
        this.paymentElementView = view;
    }

    public void registerCVCWidgetView(CVCWidget view) {
        if (view == null) {
            unbindCvcWidget();
        }
        this.cvcWidgetView = view;
    }

    // ── Bound Element Cleanup ─────────────────────────────────────────────────────────────────

    private void unbindPaymentElement() {
        if (paymentElementBound != null) {
            if (elements != null) {
                elements.unbind(paymentElementBound);
            }
            paymentElementBound.destroy();
            paymentElementBound = null;
        }
    }

    private void unbindCvcWidget() {
        if (cvcWidgetBound != null) {
            if (elements != null) {
                elements.unbind(cvcWidgetBound);
            }
            cvcWidgetBound.destroy();
            cvcWidgetBound = null;
        }
    }

    // ── Elements API ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates an Elements session for the given sdkAuthorization.
     * Mirrors: elements = hyperswitchInstance.elements(sessionConfig)
     */
    public void elements(JSObject elementsOptions, ElementsCallback callback) {
        Logger.info("Hyperswitch", "elements called");

        // Clean up existing bound elements before creating a new Elements session
        unbindPaymentElement();
        unbindCvcWidget();

        if (hyperswitchInstance == null) {
            if (callback != null)
                callback.onError("Hyperswitch not initialised — call init() first");
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

        PaymentSessionConfiguration sessionConfig = new PaymentSessionConfiguration(sdkAuthorization);

        // elements() creates its own internal PaymentSession — no separate initPaymentSession needed.
        hyperswitchInstance.elements(sessionConfig, elementsInstance -> {
            this.elements = elementsInstance;
            // Expose the Elements-internal PaymentSession so that presentPaymentSheet /
            // getCustomerSavedPaymentMethods can use it without a separate init call.
            this.paymentSession = elementsInstance.getPaymentSession();
            Logger.info("Hyperswitch", "elements ready");
            if (callback != null) callback.onReady("");
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeHiddenPaymentMethods(Map<String, Object> configMap) {
        Map<String, Object> paymentMethodLayout = (Map<String, Object>) configMap.get("paymentMethodLayout");
        if (paymentMethodLayout != null) {
            Map<String, Object> savedMethodCustomization = (Map<String, Object>) paymentMethodLayout.get("savedMethodCustomization");
            if (savedMethodCustomization != null) {
                List<Object> hiddenPaymentMethods = (List<Object>) savedMethodCustomization.get("hiddenPaymentMethods");
                if (hiddenPaymentMethods != null) {
                    Log.d("DEBUG", "type: " + hiddenPaymentMethods.getClass().getName() + " value: " + hiddenPaymentMethods);
                    ArrayList<String> list = new ArrayList<>();
                    for (Object item : hiddenPaymentMethods) {
                        if (item != null) list.add(item.toString());
                    }

                    savedMethodCustomization.put("hiddenPaymentMethods", list);
                }
            }
        }
        return configMap;
    }

    /**
     * Binds a native view to the Elements session.
     * Mirrors:
     * paymentElementBound = elements.bind(paymentElement, buildConfiguration()) { on(...) { } }
     * cvcWidgetBound      = elements.bind(cvcWidget, configMap) { on(CvcWidgetEvents.CvcStatus) { } }
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
                unbindPaymentElement();
                Map<String, Object> configMap = jsObjectToMap(createOptions);
                List<String> subscribedEventsList = extractAndRemoveSubscribedEvents(configMap);
                paymentElementBound = elements.bind(
                        paymentElementView,
                        sanitizeHiddenPaymentMethods(configMap),
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
                paymentElementBound.onPaymentResult(new PaymentResultListener() {
                    @Override
                    public void onPaymentResult(@NonNull PaymentResult paymentResult) {
                        fireEvent("onPaymentResultEvent", jsObjectToMap(paymentResultToJSObject(paymentResult)), "onPaymentResultEvent");
                    }
                });
                if(pendingConfirmCallbackRegistration.get()){
                    setPaymentConfirmButtonCallback();
                    pendingConfirmCallbackRegistration.set(false);
                }
                Logger.info("Hyperswitch", "PaymentElement bound with configuration Map");

            } else if ("cvcWidget".equalsIgnoreCase(type) || "cvc".equalsIgnoreCase(type)) {
                if (cvcWidgetView == null) {
                    Logger.error("Hyperswitch", new Throwable("CvcWidgetView not registered yet"));
                    return;
                }
                unbindCvcWidget();
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

    /**
     * Forwards a native widget event to JS via the registered NativeEventListener.
     */
    private void fireEvent(String type, Map<String, Object> payload, String source) {
        NativeEventListener listener = this.eventListener;
        if (listener != null) {
            listener.onEvent(type, payload != null ? payload : new HashMap<>(), source);
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

        sdkAuth = sdkAuthorization;
        if (paymentSession != null) {
            paymentSession.updateSdkAuthorization(sdkAuthorization);
        }

        if (elements == null) {
            if (callback != null) callback.onError("elements() must be called first");
            return;
        }

        PaymentSessionConfiguration newConfig = new PaymentSessionConfiguration(sdkAuthorization);

        for (Map.Entry<String, PaymentSessionHandler> entry : handlerRegistry.entrySet()) {
            PaymentSessionHandler handler = entry.getValue();

            if (handler != null) {
                try {
                    handler.updateSdkAuthorization(sdkAuthorization);
                } catch (Exception e) {
                    Logger.error(
                            "Hyperswitch",
                            "Failed updating handler: " + entry.getKey(),
                            e
                    );
                }
            }
        }

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
            if (callback != null)
                callback.onError("Hyperswitch not initialised — call init() first");
            return;
        }

        String sdkAuthorization = paymentSessionOptions != null
                ? paymentSessionOptions.getString("sdkAuthorization")
                : null;

        sdkAuth = sdkAuthorization;

        // initPaymentSession now takes a PaymentSessionConfiguration; the old String overload is gone.
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
            if (callback != null)
                callback.onError(new IllegalStateException("Payment session not initialised"));
            return;
        }

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("publishableKey", paymentSession.getPublishableKey());
        configMap.put("sdkAuthorization", sdkAuth);
        configMap.put("customBackendUrl", customBackendUrl);
        configMap.put("customLoggingUrl", customLoggingUrl);
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
            if (callback != null)
                callback.onError("PaymentElement not bound — call createElement() first");
            return;
        }

        activity.runOnUiThread(() ->
                paymentElementBound.confirmPayment(result -> {
                    if (callback != null) callback.onResult(paymentResultToJSObject(result));
                    return null;
                })
        );
    }

    // ── OnPaymentConfirmButtonClick ──────────────────────────────────────────────────────────

    private volatile Function1<Boolean, Unit> pendingConfirmButtonCallback;

    public void setPaymentConfirmButtonCallback() {
        if (paymentElementBound == null) {
            pendingConfirmCallbackRegistration.set(true);
        } else {
            paymentElementBound.onPaymentConfirmButtonClick(
                    (paymentRequestData, callback) -> {
                        try {
                            pendingConfirmButtonCallback = (Function1<Boolean, Unit>) callback;
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("paymentMethodType", paymentRequestData.getPaymentMethodType());
                            fireEvent("onPaymentConfirmButtonClickEvent",
                                    ConversionUtils.readableMapToMap(ConversionUtils.convertJsonToMap(jsonObject)),
                                    "onPaymentConfirmButtonClickEvent");
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        return Unit.INSTANCE;
                    }
            );
        }
    }

    public void resolvePaymentConfirmButtonClick(boolean proceed) {
        kotlin.jvm.functions.Function1<Boolean, Unit> cb = pendingConfirmButtonCallback;
        pendingConfirmButtonCallback = null;
        if (cb != null) {
            cb.invoke(proceed);
        }
    }

    // ── CustomerSavedPaymentMethods ───────────────────────────────────────────────────────────

    /**
     * Flow 1 (InitPaymentSession): asynchronously fetches the PaymentSessionHandler
     * from the active paymentSession, stores it in the registry, and returns its ID.
     */
    public void getCustomerSavedPaymentMethods(
            SavedPaymentMethodsConfiguration config,
            CustomerSavedPaymentMethodsCallback callback) {
        Logger.info("Hyperswitch", "getCustomerSavedPaymentMethods called");

        if (paymentSession == null) {
            if (callback != null)
                callback.onError("paymentSession not ready — call initPaymentSession() first");
            return;
        }

        paymentSession.getCustomerSavedPaymentMethods(config, handler -> {
            String handlerId = UUID.randomUUID().toString();
            handlerRegistry.put(handlerId, handler);
            Logger.info("Hyperswitch", "getCustomerSavedPaymentMethods ready, id: " + handlerId);
            if (callback != null) callback.onReady(handlerId);
            return null;
        });
    }

    public JSObject getCustomerSavedPaymentMethodData(String handlerId) {
        Logger.info("Hyperswitch", "getCustomerSavedPaymentMethodData called, id=" + handlerId);
        JSObject result = new JSObject();

        if (handlerId == null || handlerId.isEmpty()) {
            Logger.warn("Hyperswitch", "Invalid handlerId: null or empty");
            result.put("error", "Invalid handlerId");
            return result;
        }

        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            Logger.warn("Hyperswitch", "No handler for id: " + handlerId);
            return new JSObject();
        }

        // Use the Java-friendly callback variant — called synchronously via Result.onSuccess/onFailure.
        handler.getCustomerSavedPaymentMethodData(
                paymentMethods -> {
                    org.json.JSONArray jsonArray = new org.json.JSONArray();
                    for (int i = 0; i < paymentMethods.size(); i++) {
                        io.hyperswitch.paymentsession.PaymentMethod pm = paymentMethods.get(i);
                        try {
                            Map<String, Object> map = pm.toMap();
                            if (map != null) jsonArray.put(new org.json.JSONObject(map));
                        } catch (Exception e) {
                            Logger.error("Hyperswitch", "Failed to convert PaymentMethod at index " + i, e);
                        }
                    }
                    result.put("data", jsonArray);
                    return null;
                },
                throwable -> {
                    String msg = throwable != null ? throwable.getMessage() : "Unknown error";
                    Logger.error("Hyperswitch", "getCustomerSavedPaymentMethodData failed: " + msg, throwable);
                    result.put("error", msg);
                    return null;
                }
        );
        return result;
    }

    public JSObject getCustomerDefaultSavedPaymentMethodData(String handlerId) {
        Logger.info("Hyperswitch", "getCustomerDefaultSavedPaymentMethodData called, id=" + handlerId);
        JSObject result = new JSObject();

        if (handlerId == null || handlerId.isEmpty()) {
            Logger.warn("Hyperswitch", "Invalid handlerId: null or empty");
            result.put("error", "Invalid handlerId");
            return result;
        }

        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            Logger.warn("Hyperswitch", "No handler found for id: " + handlerId);
            result.put("error", "Handler not found");
            return result;
        }

        // Use the Java-friendly callback variant — called synchronously via Result.onSuccess/onFailure.
        handler.getCustomerDefaultSavedPaymentMethodData(
                paymentMethod -> {
                    try {
                        Map<String, Object> map = paymentMethod.toMap();
                        if (map != null) {
                            result.put("data", new org.json.JSONObject(map));
                        } else {
                            result.put("error", "toMap() returned null");
                        }
                    } catch (Exception e) {
                        Logger.error("Hyperswitch", "Failed to serialize default PaymentMethod", e);
                        result.put("error", "Serialization error: " + e.getMessage());
                    }
                    return null;
                },
                throwable -> {
                    String msg = throwable != null ? throwable.getMessage() : "Unknown error";
                    Logger.error("Hyperswitch", "getCustomerDefaultSavedPaymentMethodData failed: " + msg, throwable);
                    result.put("error", msg);
                    return null;
                }
        );
        return result;
    }

    /**
     * Fixed: was returning JSObject before the async callback fired (always empty).
     * Now callback-based, consistent with the rest of the class.
     */
    public void getCustomerLastUsedPaymentMethodData(String handlerId, PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "getCustomerLastUsedPaymentMethodData called, id=" + handlerId);
        JSObject result = new JSObject();
        if (handlerId == null || handlerId.isEmpty()) {
            Logger.warn("Hyperswitch", "Invalid handlerId: null or empty");
            result.put("error", "Invalid handlerId");
            if (callback != null) callback.onResult(result);
            return;
        }

        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            Logger.warn("Hyperswitch", "No handler found for id: " + handlerId);
            result.put("error", "Handler not found");
            if (callback != null) callback.onResult(result);
            return;
        }

        handler.getCustomerLastUsedPaymentMethodData(
                paymentMethod -> {
                    try {
                        Map<String, Object> map = paymentMethod.toMap();
                        result.put("data", new org.json.JSONObject(map));
                    } catch (Exception e) {
                        Logger.error("Hyperswitch", "Error serializing PaymentMethod", e);
                        result.put("error", "Serialization error: " + e.getMessage());
                    }
                    if (callback != null) callback.onResult(result);
                    return null;
                },
                throwable -> {
                    String msg = throwable != null ? throwable.getMessage() : "Unknown error";
                    Logger.warn("Hyperswitch", "getCustomerLastUsedPaymentMethodData failed: " + msg);
                    if (throwable instanceof io.hyperswitch.paymentsession.PMError) {
                        try {
                            result.put("error", new org.json.JSONObject(
                                    ((io.hyperswitch.paymentsession.PMError) throwable).toMap()));
                        } catch (Exception e) {
                            result.put("error", msg);
                        }
                    } else {
                        result.put("error", msg);
                    }
                    if (callback != null) callback.onResult(result);
                    return null;
                }
        );
    }

    public void confirmWithCustomerDefaultPaymentMethod(String handlerId, PaymentResultCallback callback) {
        Logger.info("Hyperswitch", "confirmWithCustomerDefaultPaymentMethod called, id=" + handlerId);
        PaymentSessionHandler handler = handlerRegistry.get(handlerId);
        if (handler == null) {
            if (callback != null) callback.onError("No handler for id: " + handlerId);
            return;
        }
        activity.runOnUiThread(() ->
                handler.confirmWithCustomerDefaultPaymentMethod(cvcWidgetView, result -> {
                    if (callback != null) callback.onResult(paymentResultToJSObject(result));
                    return null;
                })
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
                handler.confirmWithCustomerLastUsedPaymentMethod(cvcWidgetView, result -> {
                    if (callback != null) callback.onResult(paymentResultToJSObject(result));
                    return null;
                })
        );
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