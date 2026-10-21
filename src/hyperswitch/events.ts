import type {
  ElementEvent,
  PaymentEvent,
  CardDetailsChange,
  BillingDetailsChange,
  PaymentEventData,
} from './definitions';

/** Sends only current subscription names to native. */
export function withSubscriptionEvents<T extends { subscriptionEvents?: string[] }>(options: T | undefined): T {
  const result = { ...options } as T;
  if (result.subscriptionEvents) {
    result.subscriptionEvents = result.subscriptionEvents.filter(name =>
      ['cardDetailsChange', 'paymentMethodChange', 'formStatusChange', 'billingDetailsChange', 'cvcStatusChange'].includes(name)
    );
  }
  return result;
}

export function toPaymentEvent(data: PaymentEventData): PaymentEvent | null {
  return decodePaymentEvent(data.type, data.payload);
}

const ELEMENT_EVENTS: ReadonlySet<string> = new Set<ElementEvent>(['ready', 'focus', 'blur']);

function isElementEvent(name: string): name is ElementEvent {
  return ELEMENT_EVENTS.has(name);
}

/** Wraps a handler for `on(event, handler)`: lifecycle handlers just fire, with no arguments. */
export function listenerFor(event: string, handler: (event?: PaymentEvent) => void): (e: PaymentEventData) => void {
  return (data) => {
    if (isElementEvent(event)) {
      if (data.type === event) handler();
    } else if (event === 'change') {
      const change = toPaymentEvent(data);
      if (change) handler(change);
    }
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isCardDetailsChange(value: Record<string, unknown>): value is CardDetailsChange {
  const textFields = [
    'bin', 'extendedBin', 'last4', 'brand', 'expiryMonth', 'expiryYear', 'formattedExpiry',
  ] as const satisfies readonly (keyof CardDetailsChange)[];
  const flags = [
    'isCardNumberComplete', 'isCardNumberValid', 'isExpiryComplete', 'isExpiryValid', 'isCvcComplete',
  ] as const satisfies readonly (keyof CardDetailsChange)[];
  return textFields.every((key) => isNullableString(value[key])) &&
    flags.every((key) => typeof value[key] === 'boolean');
}

function isBillingDetailsChange(value: Record<string, unknown>): value is BillingDetailsChange {
  return isNullableString(value.country) &&
    isNullableString(value.state) && isNullableString(value.postalCode);
}

/** Decode the bridge contract before exposing a typed event to merchant code. */
function decodePaymentEvent(eventName: string, payload: unknown): PaymentEvent | null {
  if (!isRecord(payload)) return null;
  switch (eventName) {
    case 'cardDetailsChange':
      return isCardDetailsChange(payload) ? { eventName, payload } : null;
    case 'paymentMethodChange':
      if (typeof payload.paymentMethod !== 'string' ||
          typeof payload.paymentMethodType !== 'string' ||
          typeof payload.isSavedPaymentMethod !== 'boolean' ||
          typeof payload.isOneClickWallet !== 'boolean') return null;
      return { eventName, payload: {
        paymentMethod: payload.paymentMethod,
        paymentMethodType: payload.paymentMethodType,
        isSavedPaymentMethod: payload.isSavedPaymentMethod,
        isOneClickWallet: payload.isOneClickWallet,
      } };
    case 'formStatusChange':
      if (payload.status !== 'EMPTY' && payload.status !== 'FILLING' && payload.status !== 'COMPLETE') return null;
      return { eventName, payload: { status: payload.status } };
    case 'billingDetailsChange':
      return isBillingDetailsChange(payload) ? { eventName, payload } : null;
    case 'cvcStatusChange': {
      const status = payload.cvcStatus;
      if (!isRecord(status) || typeof status.isCvcEmpty !== 'boolean' ||
          typeof status.isCvcComplete !== 'boolean') return null;
      return { eventName, payload: { cvcStatus: {
        isCvcEmpty: status.isCvcEmpty,
        isCvcComplete: status.isCvcComplete,
      } } };
    }
    default:
      return null;
  }
}
