// ---- Config types ----

export interface CustomConfig {
  customEndpoint?: string;
  customBackendEndpoint?: string;
  customLoggingEndpoint?: string;
  customAssetEndpoint?: string;
  customSDKConfigEndpoint?: string;
  customAirborneEndpoint?: string;
}

export interface HyperConfig {
  publishableKey: string;
  profileId?: string;
  platformPublishableKey?: string;
  customConfig?: CustomConfig;
}

// ---- CVC Widget Appearance Types (matches React Native) ----

export interface CvcColors {
  primary?: string;
  background?: string;
  componentBackground?: string;
  componentBorder?: string;
  componentText?: string;
  primaryText?: string;
  secondaryText?: string;
  placeholderText?: string;
  icon?: string;
  error?: string;
  loaderBackground?: string;
  loaderForeground?: string;
}

export interface CvcColorType {
  light?: CvcColors;
  dark?: CvcColors;
}

export interface CvcOffsetType {
  x?: number;
  y?: number;
}

export interface CvcShadowConfig {
  color?: string;
  opacity?: number;
  blurRadius?: number;
  offset?: CvcOffsetType;
  intensity?: number;
}

export interface CvcShapes {
  borderRadius?: number;
  borderWidth?: number;
  shadow?: CvcShadowConfig;
}

export interface CvcFont {
  family?: string;
  scale?: number;
}

export interface CvcAppearance {
  colors?: CvcColorType;
  shapes?: CvcShapes;
  font?: CvcFont;
}

export interface CvcWidgetOptions {
  appearance?: CvcAppearance;
  placeholder?: string;
  sdkAuthorization?: string;
}

// ---- Shared types ----

export type JSONValue = Record<string, unknown>;

export interface PaymentResult {
  type: 'completed' | 'canceled' | 'failed';
  message?: string;
}

// ---- UpdateIntent result ----

export interface UpdateIntentResult {
  /** "success" | "totalFailure" | "partialFailure" */
  type: string;
  message?: string;
  failedCount?: number;
  succeededCount?: number;
}

// ---- Native widget event (pushed via notifyListeners / addListener) ----

/**
 * Native event emitted by PaymentElement or CvcWidget.
 *
 * `type` values:
 *   PaymentElement: "FORM_STATUS" | "PAYMENT_METHOD_STATUS" |
 *                   "PAYMENT_METHOD_INFO_CARD" | "PAYMENT_METHOD_INFO_BILLING_ADDRESS"
 *   CvcWidget:      "CVC_STATUS"
 *
 * `payload` is the raw key/value data from the SDK (values serialised to strings on Android).
 */
export interface PaymentEventData {
  type: string;
  payload: Record<string, string>;
}

// ---- PaymentElement ----

export interface PaymentElement {
  /**
   * Subscribe to a native widget event.
   * Use the SDK event-type string as the event name, e.g. "FORM_STATUS", "PAYMENT_METHOD_STATUS".
   * Internally wires up a Capacitor addListener("paymentEvent") and filters by type.
   */
  on(event: string, handler?: (data?: PaymentEventData) => void): void;
  collapse(): void;
  blur(): void;
  update(options: JSONValue): void;
  destroy(): void;
  unmount(): void;
  mount(selector: string): void;
  focus(): void;
  clear(): void;
  confirmPayment(options?: { confirmParams?: JSONValue }): Promise<PaymentResult>;
}

// ---- CvcWidget ----

export interface CvcWidget {
  /**
   * Mount the CVC widget to a DOM selector.
   * @param selector - CSS selector for the container element
   * @param options - Optional configuration for appearance and behavior
   */
  mount(selector: string, options?: CvcWidgetOptions): void;
  unmount(): void;
  destroy(): void;
  /**
   * Subscribe to CVC widget events.
   * @param event - Event name ('change' for CVC status updates)
   * @param handler - Callback function for the event
   */
  on(event: string, handler?: (data?: PaymentEventData) => void): void;
}

// ---- PaymentSessionHandler ----
// A first-class JS wrapper around the native PaymentSessionHandler.
// Returned by Elements.getCustomerSavedPaymentMethods() and
// InitPaymentSession.getCustomerSavedPaymentMethods().

export interface PaymentSessionHandler {
  /** The opaque ID used to route calls to the correct native handler instance. */
  readonly handlerId: string;

  /** All saved payment methods for the customer. */
  getCustomerSavedPaymentMethodData(): Promise<JSONValue>;

  /** The customer's default saved payment method. */
  getCustomerDefaultSavedPaymentMethodData(): Promise<JSONValue>;

  /** The customer's most recently used saved payment method. */
  getCustomerLastUsedPaymentMethodData(): Promise<JSONValue>;

  /** Confirm with the default saved method (uses mounted CvcWidget if present). */
  confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult>;

  /** Confirm with the last-used saved method (uses mounted CvcWidget if present). */
  confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult>;
}

// ---- Elements ----

export interface Elements {
  create(options: { type: 'paymentElement' }): PaymentElement;
  create(options: { type: 'cvcWidget'; options?: CvcWidgetOptions }): CvcWidget;
  updateIntent(intentResolver: () => Promise<string>): Promise<UpdateIntentResult>;

  /**
   * Fetches saved payment methods and returns a PaymentSessionHandler
   * wrapper whose methods operate on that specific handler instance.
   */
  getCustomerSavedPaymentMethods(): Promise<PaymentSessionHandler>;
}

// ---- InitPaymentSession (legacy sheet flow) ----

export interface InitPaymentSession {
  presentPaymentSheet(options?: JSONValue): Promise<PaymentResult>;

  /**
   * Fetches saved payment methods and returns a PaymentSessionHandler
   * wrapper whose methods operate on that specific handler instance.
   */
  getCustomerSavedPaymentMethods(): Promise<PaymentSessionHandler>;
}

// ---- Top-level session ----

export interface HyperswitchSession {
  elements(options: { sdkAuthorization: string }): Promise<Elements>;
  initPaymentSession(options: { sdkAuthorization: string }): Promise<InitPaymentSession>;
}

// ---- Native bridge interface ----

export interface HyperswitchPlugin {
  init(config: HyperConfig): Promise<void>;

  // Elements session
  elements(options: { elementsOptions: JSONValue }): Promise<{ handlerId: string }>;
  createElement(options: { type: string; createOptions: JSONValue }): Promise<void>;
  updateIntent(options: { sdkAuthorization: string }): Promise<UpdateIntentResult>;

  // Fetch saved methods — returns a handlerId the JS side holds onto
  getCustomerSavedPaymentMethods(): Promise<{ handlerId: string }>;

  // Handler-scoped methods — handlerId routes to the right native instance
  getCustomerSavedPaymentMethodData(options: { handlerId: string }): Promise<JSONValue>;
  getCustomerDefaultSavedPaymentMethodData(options: { handlerId: string }): Promise<JSONValue>;
  getCustomerLastUsedPaymentMethodData(options: { handlerId: string }): Promise<JSONValue>;
  confirmWithCustomerDefaultPaymentMethod(options: { handlerId: string }): Promise<PaymentResult>;
  confirmWithCustomerLastUsedPaymentMethod(options: { handlerId: string }): Promise<PaymentResult>;

  // PaymentElement confirm
  confirmPayment(options: { confirmParams: JSONValue }): Promise<PaymentResult>;

  // Legacy payment session
  initPaymentSession(options: { paymentSessionOptions: JSONValue }): Promise<void>;
  presentPaymentSheet(options: { sheetOptions: JSONValue }): Promise<PaymentResult>;

  // PaymentElement lifecycle (elementOn is a no-op — subscriptions are set up natively)
  elementOn(options: { event: string }): Promise<void>;
  elementCollapse(): Promise<void>;
  elementBlur(): Promise<void>;
  elementUpdate(options: { updateOptions: JSONValue }): Promise<void>;
  elementDestroy(): Promise<void>;
  elementUnmount(): Promise<void>;
  elementMount(options: { selector: string }): Promise<void>;
  elementFocus(): Promise<void>;
  elementClear(): Promise<void>;

  /**
   * Subscribe to native widget events emitted by PaymentElement or CvcWidget.
   * The handler receives a PaymentEventData object with `type` and `payload`.
   *
   * Use `element.on(eventType, handler)` in the JS wrapper layer instead of
   * calling this directly.
   */
  addListener(
    event: 'paymentEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;
}
