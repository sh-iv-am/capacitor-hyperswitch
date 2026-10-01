import { ElementEvent, PaymentEvent, PaymentRequestData, PaymentResult } from './PaymentTypes';
import { LegacySubscriptionEvent } from './PaymentSheetTypes';

export type removeListenerFunction = Promise<{ remove: () => void }> | { remove: () => void };
export interface PaymentElement {
  /** Receives every event listed in `subscriptionEvents`; filter on `event.eventName`. */
  on(event: 'change', handler?: (event: PaymentEvent) => void): removeListenerFunction;
  /** Lifecycle events; see {@link ElementEvent}. */
  on(event: ElementEvent, handler?: () => void): removeListenerFunction;
  /** @deprecated Use `on('change', …)` and filter on `event.eventName`. */
  on(event: LegacySubscriptionEvent | string, handler?: (event: PaymentEvent) => void): removeListenerFunction;
  onPaymentResult(handler?: (data: PaymentResult) => void): removeListenerFunction;
  onPaymentConfirmButtonClick(handler?: (data: PaymentRequestData) => boolean): removeListenerFunction;
  collapse(): void;
  blur(): void;
  update(options: Record<string, Object>): void;
  destroy(): void;
  unmount(): void;
  mount(selector: string): void;
  focus(): void;
  clear(): void;
  confirmPayment(options?: { confirmParams?: Record<string, Object> }): Promise<PaymentResult>;
}

import { PaymentSheetOptions as PaymentElementOptions } from './PaymentSheetTypes';

export { PaymentElementOptions };

export interface PaymentElementHandle {
  confirmPayment(options?: { confirmParams?: Record<string, Object> }): Promise<PaymentResult>;
  collapse(): void;
  focus(): void;
  blur(): void;
  clear(): void;
  update(options: Record<string, Object>): void;
  destroy(): void;
}

export interface PaymentElementProps {
  id?: string;
  options?: PaymentElementOptions;
  /** Fires once the element has finished loading (payment methods fetched), not on mount. */
  onReady?: () => void;
  /** Fires when focus enters the element (moving between its fields does not re-fire). */
  onFocus?: () => void;
  /** Fires when focus leaves the element entirely (moving between its fields does not fire). */
  onBlur?: () => void;
  onChange?: (event: PaymentEvent) => void;
  onPaymentResult?: (data: PaymentResult) => void;
  onPaymentConfirmButtonClick?: (data: PaymentRequestData) => boolean;
  className?: string;
  style?: string;
}
