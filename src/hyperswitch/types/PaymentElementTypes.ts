import { PaymentEventData, PaymentResult } from './PaymentTypes';

export interface PaymentElement {
  on(event: string, handler?: (data?: PaymentEventData) => void): void;
  onPaymentResult(handler?: (data: PaymentResult) => void): void;
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
  onReady?: () => void;
  onChange?: (data?: PaymentEventData) => void;
  onPaymentResult?: (data: PaymentResult) => void;
  className?: string;
  style?: string;
}
