import type { SubscriptionEvent } from './PaymentSheetTypes';

export interface PaymentResult {
  type: 'completed' | 'canceled' | 'failed';
  message?: string;
}

/** Raw event as it crosses the plugin bridge; for payment events `type` is the event name. */
export interface PaymentEventData {
  type: string;
  payload: Record<string, any>;
}

/** Delivered to `on('change')`: switch on `eventName`, read the details from `payload`. */
export interface PaymentEvent {
  eventName: SubscriptionEvent;
  payload: Record<string, any>;
}

/**
 * Element lifecycle events: `ready` once the element has loaded (on mount for the CVC widget),
 * `focus`/`blur` when focus enters/leaves the element. They need no subscription, never reach
 * `on('change')`, and their handlers take no arguments.
 */
export type ElementEvent = 'ready' | 'focus' | 'blur';

type paymentMethodType = 'CARD' | 'APPLE_PAY' | 'GOOGLE_PAY' | 'SAMSUNG_PAY' | 'PAYPAL' | string;

export interface PaymentRequestData {
  paymentMethodType: paymentMethodType;
  [key: string]: any;
}
