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

// ---- Shared types ----

export type JSONValue = Record<string, unknown>;

export interface PaymentResult {
  type: 'completed' | 'canceled' | 'failed';
  message?: string;
}

// ---- PaymentElement ----

export interface PaymentElement {
  on(event: string, handler?: (data?: JSONValue) => void): void;
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
  mount(selector: string): void;
  unmount(): void;
  destroy(): void;
}

// ---- Elements ----

export interface Elements {
  create(options: { type: 'paymentElement' }): PaymentElement;
  createCvcWidget(): CvcWidget;
  updateIntent(intentResolver: () => Promise<string>): Promise<JSONValue>;
  getCustomerDefaultSavedPaymentMethodData(): Promise<JSONValue>;
  getCustomerLastUsedPaymentMethodData(): Promise<JSONValue>;
  confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult>;
  confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult>;
}

// ---- InitPaymentSession (legacy sheet flow) ----

export interface InitPaymentSession {
  presentPaymentSheet(options?: JSONValue): Promise<PaymentResult>;
  getCustomerSavedPaymentMethods(): Promise<JSONValue>;
  getCustomerDefaultSavedPaymentMethodData(): Promise<JSONValue>;
  getCustomerLastUsedPaymentMethodData(): Promise<JSONValue>;
  confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult>;
  confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult>;
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
  elements(options: { elementsOptions: JSONValue }): Promise<void>;
  createElement(options: { type: string; createOptions: JSONValue }): Promise<void>;
  updateIntent(options: { sdkAuthorization: string }): Promise<JSONValue>;

  // Saved payment method data
  getCustomerSavedPaymentMethods(): Promise<JSONValue>;
  getCustomerDefaultSavedPaymentMethodData(): Promise<JSONValue>;
  getCustomerLastUsedPaymentMethodData(): Promise<JSONValue>;

  // Confirm with saved methods
  confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult>;
  confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult>;

  // PaymentElement confirm
  confirmPayment(options: { confirmParams: JSONValue }): Promise<PaymentResult>;

  // Legacy payment session
  initPaymentSession(options: { paymentSessionOptions: JSONValue }): Promise<void>;
  presentPaymentSheet(options: { sheetOptions: JSONValue }): Promise<PaymentResult>;

  // PaymentElement lifecycle
  elementOn(options: { event: string }): Promise<JSONValue | void>;
  elementCollapse(): Promise<void>;
  elementBlur(): Promise<void>;
  elementUpdate(options: { updateOptions: JSONValue }): Promise<void>;
  elementDestroy(): Promise<void>;
  elementUnmount(): Promise<void>;
  elementMount(options: { selector: string }): Promise<void>;
  elementFocus(): Promise<void>;
  elementClear(): Promise<void>;
}
