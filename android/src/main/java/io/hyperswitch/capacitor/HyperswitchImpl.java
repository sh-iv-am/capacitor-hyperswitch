package io.hyperswitch.capacitor;

import androidx.appcompat.app.AppCompatActivity;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import io.hyperswitch.paymentsheet.AddressDetails;
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

        PaymentSheet.Configuration configuration = buildPaymentSheetConfigurationObject(sheetOptions);
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
            config.put("subscribedEvents", new String[]{"CVC_STATUS"});
            return config;
        }

        JSObject appearanceObj = createOptions.getJSObject("appearance");
        if (appearanceObj != null) {
            Map<String, Object> appearanceMap = convertJsObjectToMap(appearanceObj);
            if (!appearanceMap.isEmpty()) {
                config.put("appearance", appearanceMap);
            }
        }

        String placeholder = createOptions.getString("placeholder");
        if (placeholder != null && !placeholder.isEmpty()) {
            Map<String, Object> placeholderMap = new HashMap<>();
            placeholderMap.put("cvv", placeholder);
            config.put("placeholder", placeholderMap);
        }

        String sdkAuth = createOptions.getString("sdkAuthorization");
        if (sdkAuth != null && !sdkAuth.isEmpty()) {
            config.put("sdkAuthorization", sdkAuth);
        }

        config.put("subscribedEvents", new String[]{"CVC_STATUS"});

        return config;
    }
    
    private PaymentSheet.Configuration buildPaymentSheetConfigurationObject(JSObject sheetOptions) {
        String merchantDisplayName = "Merchant";
        if (sheetOptions != null) {
            String mdn = sheetOptions.getString("merchantDisplayName");
            if (mdn != null && !mdn.isEmpty()) merchantDisplayName = mdn;
        }

        if (sheetOptions == null) {
            return new PaymentSheet.Configuration(merchantDisplayName);
        }

        PaymentSheet.Appearance appearance = buildAppearance(sheetOptions.getJSObject("appearance"));
        PaymentSheet.PlaceHolder placeHolder = buildPlaceHolder(sheetOptions.getJSObject("placeholder"));
        PaymentSheet.CustomerConfiguration customer = buildCustomerConfiguration(sheetOptions.getJSObject("customer"));
        AddressDetails shippingDetails = buildAddressDetails(sheetOptions.getJSObject("shippingDetails"));
        PaymentSheet.BillingDetails billingDetails = buildBillingDetails(sheetOptions.getJSObject("defaultBillingDetails"));

        String primaryButtonLabel = sheetOptions.getString("primaryButtonLabel");
        String paymentSheetHeaderLabel = sheetOptions.getString("paymentSheetHeaderLabel");
        String savedPaymentSheetHeaderLabel = sheetOptions.getString("savedPaymentSheetHeaderLabel");

        boolean allowsDelayed = sheetOptions.has("allowsDelayedPaymentMethods")
                && sheetOptions.optBoolean("allowsDelayedPaymentMethods", false);
        boolean allowsShipping = sheetOptions.has("allowsPaymentMethodsRequiringShippingAddress")
                && sheetOptions.optBoolean("allowsPaymentMethodsRequiringShippingAddress", false);

        Boolean displaySavedPaymentMethods = sheetOptions.has("displaySavedPaymentMethods")
                ? sheetOptions.optBoolean("displaySavedPaymentMethods", true) : null;
        Boolean displaySavedPaymentMethodsCheckbox = sheetOptions.has("displaySavedPaymentMethodsCheckbox")
                ? sheetOptions.optBoolean("displaySavedPaymentMethodsCheckbox", true) : null;
        Boolean displayDefaultSavedPaymentIcon = sheetOptions.has("displayDefaultSavedPaymentIcon")
                ? sheetOptions.optBoolean("displayDefaultSavedPaymentIcon", true) : null;
        Boolean hideConfirmButton = sheetOptions.has("hideConfirmButton")
                ? sheetOptions.optBoolean("hideConfirmButton", false) : null;
        Boolean disableBranding = sheetOptions.has("disableBranding")
                ? sheetOptions.optBoolean("disableBranding", false) : null;
        Boolean defaultView = sheetOptions.has("defaultView")
                ? sheetOptions.optBoolean("defaultView", false) : null;

        ColorStateList primaryButtonColorCSL = null;
        String primaryButtonColor = sheetOptions.getString("primaryButtonColor");
        if (primaryButtonColor != null && !primaryButtonColor.isEmpty()) {
            primaryButtonColorCSL = ColorStateList.valueOf(Color.parseColor(primaryButtonColor));
        }

        return new PaymentSheet.Configuration(
                merchantDisplayName,
                customer,
                null,
                primaryButtonColorCSL,
                billingDetails,
                shippingDetails,
                allowsDelayed,
                allowsShipping,
                appearance,
                primaryButtonLabel,
                paymentSheetHeaderLabel,
                savedPaymentSheetHeaderLabel,
                displayDefaultSavedPaymentIcon,
                displaySavedPaymentMethodsCheckbox,
                displaySavedPaymentMethods,
                placeHolder,
                hideConfirmButton,
                null,
                disableBranding,
                defaultView,
                false,
                null
        );
    }

    private PaymentSheet.Appearance buildAppearance(JSObject appearanceObj) {
        if (appearanceObj == null) return null;

        PaymentSheet.Colors colorsLight = null;
        PaymentSheet.Colors colorsDark = null;
        JSObject colorsObj = appearanceObj.getJSObject("colors");
        if (colorsObj != null) {
            colorsLight = buildColors(colorsObj.getJSObject("light"));
            colorsDark = buildColors(colorsObj.getJSObject("dark"));
        }

        PaymentSheet.Shapes shapes = buildShapes(appearanceObj.getJSObject("shapes"));
        PaymentSheet.Typography typography = buildTypography(appearanceObj.getJSObject("font"));
        PaymentSheet.PrimaryButton primaryButton = buildPrimaryButton(appearanceObj.getJSObject("primaryButton"));

        PaymentSheet.Theme theme = null;
        String themeStr = appearanceObj.getString("theme");
        if (themeStr != null && !themeStr.isEmpty()) {
            try { theme = PaymentSheet.Theme.valueOf(themeStr); }
            catch (IllegalArgumentException ignored) {}
        }

        String locale = appearanceObj.getString("locale");

        return new PaymentSheet.Appearance(colorsLight, colorsDark, shapes, typography, primaryButton, locale, theme);
    }

    private PaymentSheet.Colors buildColors(JSObject colorsObj) {
        if (colorsObj == null) return null;

        return new PaymentSheet.Colors(
                parseOptionalColor(colorsObj.getString("primary")),
                parseOptionalColor(colorsObj.getString("background")),
                parseOptionalColor(colorsObj.getString("componentBackground")),
                parseOptionalColor(colorsObj.getString("componentBorder")),
                parseOptionalColor(colorsObj.getString("componentDivider")),
                parseOptionalColor(colorsObj.getString("componentText")),
                parseOptionalColor(colorsObj.getString("primaryText")),
                parseOptionalColor(colorsObj.getString("secondaryText")),
                parseOptionalColor(colorsObj.getString("placeholderText")),
                parseOptionalColor(colorsObj.getString("icon")),
                parseOptionalColor(colorsObj.getString("error")),
                parseOptionalColor(colorsObj.getString("loaderBackground")),
                parseOptionalColor(colorsObj.getString("loaderForeground"))
        );
    }

    private Integer parseOptionalColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) return null;
        try { return Color.parseColor(hexColor); }
        catch (IllegalArgumentException e) { return null; }
    }

    private PaymentSheet.Shapes buildShapes(JSObject shapesObj) {
        if (shapesObj == null) return null;

        Float cornerRadiusDp = shapesObj.has("borderRadius")
                ? (float) shapesObj.optDouble("borderRadius", 0) : null;
        Float borderStrokeWidthDp = shapesObj.has("borderWidth")
                ? (float) shapesObj.optDouble("borderWidth", 0) : null;
        PaymentSheet.Shadow shadow = buildShadow(shapesObj.getJSObject("shadow"));

        return new PaymentSheet.Shapes(cornerRadiusDp, borderStrokeWidthDp, shadow);
    }

    private PaymentSheet.Shadow buildShadow(JSObject shadowObj) {
        if (shadowObj == null) return null;
        Integer color = parseOptionalColor(shadowObj.getString("color"));
        Float intensity = shadowObj.has("intensity")
                ? (float) shadowObj.optDouble("intensity", 0) : null;
        return new PaymentSheet.Shadow(color, intensity);
    }

    private PaymentSheet.Typography buildTypography(JSObject fontObj) {
        if (fontObj == null) return null;
        Float sizeScaleFactor = fontObj.has("scale")
                ? (float) fontObj.optDouble("scale", 1.0) : null;
        String fontFamily = fontObj.getString("family");
        return new PaymentSheet.Typography(sizeScaleFactor, null, fontFamily);
    }

    private PaymentSheet.PrimaryButton buildPrimaryButton(JSObject pbObj) {
        if (pbObj == null) return null;
        PaymentSheet.PrimaryButtonColors colorsLight = buildPrimaryButtonColors(pbObj.getJSObject("colorsLight"));
        PaymentSheet.PrimaryButtonColors colorsDark = buildPrimaryButtonColors(pbObj.getJSObject("colorsDark"));
        PaymentSheet.PrimaryButtonShape shape = buildPrimaryButtonShape(pbObj.getJSObject("shape"));
        return new PaymentSheet.PrimaryButton(colorsLight, colorsDark, shape, null);
    }

    private PaymentSheet.PrimaryButtonColors buildPrimaryButtonColors(JSObject pbcObj) {
        if (pbcObj == null) return null;
        return new PaymentSheet.PrimaryButtonColors(
                parseOptionalColor(pbcObj.getString("background")),
                parseOptionalColor(pbcObj.getString("onBackground")),
                parseOptionalColor(pbcObj.getString("border"))
        );
    }

    private PaymentSheet.PrimaryButtonShape buildPrimaryButtonShape(JSObject pbsObj) {
        if (pbsObj == null) return null;
        Float cornerRadiusDp = pbsObj.has("cornerRadius")
                ? (float) pbsObj.optDouble("cornerRadius", 0) : null;
        Float borderStrokeWidthDp = pbsObj.has("borderWidth")
                ? (float) pbsObj.optDouble("borderWidth", 0) : null;
        PaymentSheet.Shadow shadow = buildShadow(pbsObj.getJSObject("shadow"));
        return new PaymentSheet.PrimaryButtonShape(cornerRadiusDp, borderStrokeWidthDp, shadow);
    }

    private PaymentSheet.PlaceHolder buildPlaceHolder(JSObject phObj) {
        if (phObj == null) return null;
        return new PaymentSheet.PlaceHolder(
                phObj.getString("cardNumber"),
                phObj.getString("expiryDate"),
                phObj.getString("cvv")
        );
    }

    private PaymentSheet.CustomerConfiguration buildCustomerConfiguration(JSObject custObj) {
        if (custObj == null) return null;
        return new PaymentSheet.CustomerConfiguration(
                custObj.getString("id"),
                custObj.getString("ephemeralKeySecret")
        );
    }

    private PaymentSheet.BillingDetails buildBillingDetails(JSObject bdObj) {
        if (bdObj == null) return null;
        return new PaymentSheet.BillingDetails(
                buildAddress(bdObj.getJSObject("address")),
                bdObj.getString("email"),
                bdObj.getString("name"),
                extractPhoneNumber(bdObj.getJSObject("phone"))
        );
    }

    private AddressDetails buildAddressDetails(JSObject sdObj) {
        if (sdObj == null) return null;
        return new AddressDetails(
                sdObj.getString("name"),
                buildAddress(sdObj.getJSObject("address")),
                extractPhoneNumber(sdObj.getJSObject("phone")),
                null
        );
    }

    private PaymentSheet.Address buildAddress(JSObject addrObj) {
        if (addrObj == null) return null;
        return new PaymentSheet.Address(
                addrObj.getString("city"),
                addrObj.getString("country"),
                addrObj.getString("line1"),
                addrObj.getString("line2"),
                addrObj.getString("zip"),
                addrObj.getString("state")
        );
    }

    private String extractPhoneNumber(JSObject phoneObj) {
        if (phoneObj == null) return null;
        return phoneObj.getString("number");
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

        JSObject customerObj = createOptions.getJSObject("customer");
        if (customerObj != null) {
            Map<String, Object> customerMap = convertJsObjectToMap(customerObj);
            if (!customerMap.isEmpty()) {
                config.put("customer", customerMap);
            }
        }

        JSObject shippingObj = createOptions.getJSObject("shippingDetails");
        if (shippingObj != null) {
            Map<String, Object> shippingMap = convertJsObjectToMap(shippingObj);
            if (!shippingMap.isEmpty()) {
                config.put("shippingDetails", shippingMap);
            }
        }

        JSObject billingObj = createOptions.getJSObject("defaultBillingDetails");
        if (billingObj != null) {
            Map<String, Object> billingMap = convertJsObjectToMap(billingObj);
            if (!billingMap.isEmpty()) {
                config.put("defaultBillingDetails", billingMap);
            }
        }

        String merchantDisplayName = createOptions.getString("merchantDisplayName");
        if (merchantDisplayName != null && !merchantDisplayName.isEmpty()) {
            config.put("merchantDisplayName", merchantDisplayName);
        } else {
            config.put("merchantDisplayName", "Merchant");
        }

        String primaryButtonLabel = createOptions.getString("primaryButtonLabel");
        if (primaryButtonLabel != null && !primaryButtonLabel.isEmpty()) {
            config.put("primaryButtonLabel", primaryButtonLabel);
        }

        String primaryButtonColor = createOptions.getString("primaryButtonColor");
        if (primaryButtonColor != null) {
            config.put("primaryButtonColor", primaryButtonColor);
        }

        String paymentSheetHeaderLabel = createOptions.getString("paymentSheetHeaderLabel");
        if (paymentSheetHeaderLabel != null && !paymentSheetHeaderLabel.isEmpty()) {
            config.put("paymentSheetHeaderLabel", paymentSheetHeaderLabel);
        }

        String savedPaymentSheetHeaderLabel = createOptions.getString("savedPaymentSheetHeaderLabel");
        if (savedPaymentSheetHeaderLabel != null && !savedPaymentSheetHeaderLabel.isEmpty()) {
            config.put("savedPaymentSheetHeaderLabel", savedPaymentSheetHeaderLabel);
        }

        if (createOptions.has("allowsDelayedPaymentMethods")) {
            config.put("allowsDelayedPaymentMethods", createOptions.optBoolean("allowsDelayedPaymentMethods", false));
        }

        if (createOptions.has("allowsPaymentMethodsRequiringShippingAddress")) {
            config.put("allowsPaymentMethodsRequiringShippingAddress", createOptions.optBoolean("allowsPaymentMethodsRequiringShippingAddress", false));
        }

        if (createOptions.has("displaySavedPaymentMethods")) {
            config.put("displaySavedPaymentMethods", createOptions.optBoolean("displaySavedPaymentMethods", true));
        }

        if (createOptions.has("displaySavedPaymentMethodsCheckbox")) {
            config.put("displaySavedPaymentMethodsCheckbox", createOptions.optBoolean("displaySavedPaymentMethodsCheckbox", true));
        }

        if (createOptions.has("displayDefaultSavedPaymentIcon")) {
            config.put("displayDefaultSavedPaymentIcon", createOptions.optBoolean("displayDefaultSavedPaymentIcon", true));
        }

        if (createOptions.has("disableBranding")) {
            config.put("disableBranding", createOptions.optBoolean("disableBranding", false));
        }

        if (createOptions.has("defaultView")) {
            config.put("defaultView", createOptions.optBoolean("defaultView", false));
        }

        if (createOptions.has("enablePartialLoading")) {
            config.put("enablePartialLoading", createOptions.optBoolean("enablePartialLoading", false));
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
                        key.equals("light") || key.equals("dark") ||
                        key.equals("primaryButton") || key.equals("shadow") ||
                        key.equals("offset");
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
               key.equals("intensity") ||
               key.equals("headingTextSizeAdjust") ||
               key.equals("subHeadingTextSizeAdjust") ||
               key.equals("placeholderTextSizeAdjust") ||
               key.equals("buttonTextSizeAdjust") ||
               key.equals("errorTextSizeAdjust") ||
               key.equals("linkTextSizeAdjust") ||
               key.equals("modalTextSizeAdjust") ||
               key.equals("cardTextSizeAdjust") ||
               key.equals("fontSizeSp");
    }
}