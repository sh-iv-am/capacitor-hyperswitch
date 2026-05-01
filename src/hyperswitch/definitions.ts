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

// ---- PaymentSheet Configuration Types (matches React Native) ----

export type SubscriptionEvent =
  | 'PAYMENT_METHOD_INFO_CARD'
  | 'PAYMENT_METHOD_STATUS'
  | 'FORM_STATUS'
  | 'PAYMENT_METHOD_INFO_ADDRESS'
  | 'CVC_STATUS';

export type Theme = 'Default' | 'Light' | 'Dark' | 'Minimal' | 'FlatMinimal';
export type Layout = 'tabs' | 'accordion' | 'spacedAccordion';

export interface Colors {
  primary?: string;
  background?: string;
  componentBackground?: string;
  componentBorder?: string;
  componentDivider?: string;
  componentText?: string;
  primaryText?: string;
  secondaryText?: string;
  placeholderText?: string;
  icon?: string;
  error?: string;
  loaderBackground?: string;
  loaderForeground?: string;
}

export interface ColorType {
  light?: Colors;
  dark?: Colors;
}

export interface OffsetType {
  x?: number;
  y?: number;
}

export interface ShadowConfig {
  color?: string;
  opacity?: number;
  blurRadius?: number;
  offset?: OffsetType;
  intensity?: number;
}

export interface Shapes {
  borderRadius?: number;
  borderWidth?: number;
  shadow?: ShadowConfig;
}

export interface Font {
  family?: string;
  scale?: number;
  headingTextSizeAdjust?: number;
  subHeadingTextSizeAdjust?: number;
  placeholderTextSizeAdjust?: number;
  buttonTextSizeAdjust?: number;
  errorTextSizeAdjust?: number;
  linkTextSizeAdjust?: number;
  modalTextSizeAdjust?: number;
  cardTextSizeAdjust?: number;
}

export interface PrimaryButtonColors {
  background?: string;
  text?: string;
  border?: string;
}

export interface PrimaryButtonColorType {
  light?: PrimaryButtonColors;
  dark?: PrimaryButtonColors;
}

export interface PrimaryButton {
  shapes?: Shapes;
  colors?: PrimaryButtonColorType;
}

export type GooglePayButtonType = 'BUY' | 'BOOK' | 'CHECKOUT' | 'DONATE' | 'ORDER' | 'PAY' | 'SUBSCRIBE' | 'PLAIN';
export type GooglePayButtonStyle = 'light' | 'dark';

export interface GooglePayThemeBaseStyle {
  light?: GooglePayButtonStyle;
  dark?: GooglePayButtonStyle;
}

export interface GooglePayConfiguration {
  buttonType?: GooglePayButtonType;
  buttonStyle?: GooglePayThemeBaseStyle;
}

export type ApplePayButtonType = 'buy' | 'setUp' | 'inStore' | 'donate' | 'checkout' | 'book' | 'subscribe' | 'plain';
export type ApplePayButtonStyle = 'white' | 'whiteOutline' | 'black';

export interface ApplePayThemeBaseStyle {
  light?: ApplePayButtonStyle;
  dark?: ApplePayButtonStyle;
}

export interface ApplePayConfiguration {
  buttonType?: ApplePayButtonType;
  buttonStyle?: ApplePayThemeBaseStyle;
}

export interface Appearance {
  colors?: ColorType;
  shapes?: Shapes;
  font?: Font;
  primaryButton?: PrimaryButton;
  googlePay?: GooglePayConfiguration;
  applePay?: ApplePayConfiguration;
  theme?: Theme;
  layout?: Layout;
  locale?: string;
}

export interface Placeholder {
  cardNumber?: string;
  expiryDate?: string;
  cvv?: string;
}

export interface Address {
  first_name?: string;
  last_name?: string;
  city?: string;
  country?: string;
  line1?: string;
  line2?: string;
  zip?: string;
  state?: string;
}

export interface Phone {
  number?: string;
  country_code?: string;
}

export interface AddressDetails {
  address?: Address;
  email?: string;
  phone?: Phone;
}

export interface CustomerConfiguration {
  id?: string;
  ephemeralKeySecret?: string;
}

export interface PaymentSheetOptions {
  sdkAuthorization: string;
  allowsDelayedPaymentMethods?: boolean;
  appearance?: Appearance;
  shippingDetails?: AddressDetails;
  primaryButtonLabel?: string;
  paymentSheetHeaderText?: string;
  savedPaymentScreenHeaderText?: string;
  merchantDisplayName?: string;
  defaultBillingDetails?: AddressDetails;
  primaryButtonColor?: string;
  allowsPaymentMethodsRequiringShippingAddress?: boolean;
  displaySavedPaymentMethodsCheckbox?: boolean;
  displaySavedPaymentMethods?: boolean;
  placeholder?: Placeholder;
  defaultView?: boolean;
  disableBranding?: boolean;
  netceteraSDKApiKey?: string;
  displayDefaultSavedPaymentIcon?: boolean;
  enablePartialLoading?: boolean;
  customer?: CustomerConfiguration;
  paymentSheetHeaderLabel?: string;
  savedPaymentSheetHeaderLabel?: string;
  subscribedEvents?: SubscriptionEvent[];
  hideConfirmButton?: boolean;
}

// ---- CVC Widget Types (subset of Appearance, matches React Native) ----

export interface CvcAppearance {
  colors?: ColorType;
  shapes?: Shapes;
  font?: Pick<Font, 'family' | 'scale'>;
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
  create(options: { type: 'paymentElement'; options?: PaymentSheetOptions }): PaymentElement;
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
    event: 'paymentElementEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;

  addListener(
    event: 'cvcWidgetEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;
}
