import { HyperswitchConfiguration } from './types/HyperswitchSessionTypes';
import { PaymentSessionConfiguration } from './types/HyperswitchSessionTypes';
import { PaymentResult, PaymentEventData } from './definitions';
import { PaymentMethodData, PaymentMethodListData } from './types/PaymentMethodTypes';
import { PaymentSheetOptions, SavedPaymentMethodsConfiguration } from './definitions';
import { PaymentElementOptions } from './definitions';
import { CvcWidgetOptions } from './definitions';

export type * from './types/AppearanceTypes';
export type * from './types/CustomerLastUsedPaymentMethodTypes';
export type * from './types/CustomerDefaultSavedPaymentMethodTypes';
export type * from './types/CustomerSavedPaymentMethodsSessionTypes';
export type * from './types/CvcWidgetTypes';
export type * from './types/ElementsTypes';
export type * from './types/HyperswitchSessionTypes';
export type * from './types/PaymentElementTypes';
export type * from './types/PaymentMethodTypes';
export type * from './types/PaymentSessionTypes';
export type * from './types/PaymentSheetTypes';
export type * from './types/PaymentTypes';

export interface HyperswitchPlugin {
  init(config: HyperswitchConfiguration): Promise<void>;

  // Elements session
  elements(options: { elementsOptions: PaymentSessionConfiguration }): Promise<{ handlerId: string }>;
  createElement(options: { type: string; createOptions: PaymentElementOptions | CvcWidgetOptions }): Promise<void>;
  updateIntent(options: PaymentSessionConfiguration): Promise<void>;
  setPaymentConfirmButtonCallback(): Promise<void>;

  // Fetch saved methods — returns a handlerId the JS side holds onto
  getCustomerSavedPaymentMethods(options?: {
    configuration?: SavedPaymentMethodsConfiguration;
  }): Promise<{ handlerId: string }>;

  // Handler-scoped methods — handlerId routes to the right native instance
  getCustomerSavedPaymentMethodData(options: { handlerId: string }): Promise<PaymentMethodListData>;
  getCustomerDefaultSavedPaymentMethodData(options: { handlerId: string }): Promise<PaymentMethodData>;
  getCustomerLastUsedPaymentMethodData(options: { handlerId: string }): Promise<PaymentMethodData>;
  confirmWithCustomerDefaultPaymentMethod(options: { handlerId: string }): Promise<PaymentResult>;
  confirmWithCustomerLastUsedPaymentMethod(options: { handlerId: string }): Promise<PaymentResult>;

  // PaymentElement confirm
  confirmPayment(options: { confirmParams: Record<string, Object> }): Promise<PaymentResult>;

  resolvePaymentConfirmButtonClick(options: { proceed: boolean }): Promise<void>;

  // Legacy payment session
  initPaymentSession(options: { paymentSessionOptions: PaymentSessionConfiguration }): Promise<void>;
  presentPaymentSheet(options: { sheetOptions: PaymentSheetOptions }): Promise<PaymentResult>;

  // PaymentElement lifecycle (elementOn is a no-op — subscriptions are set up natively)
  elementOn(options: { event: string }): Promise<void>;
  elementCollapse(): Promise<void>;
  elementBlur(): Promise<void>;
  elementUpdate(options: { updateOptions: Record<string, Object> }): Promise<void>;
  elementDestroy(): Promise<void>;
  elementUnmount(): Promise<void>;
  elementMount(options: { selector: string }): Promise<void>;
  elementFocus(): Promise<void>;
  elementClear(): Promise<void>;
  addListener(
    event: 'paymentElementEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;

  addListener(
    event: 'cvcWidgetEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    event: 'onPaymentResultEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    event: 'onPaymentConfirmButtonClickEvent',
    handler: (data: PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }>;
}
