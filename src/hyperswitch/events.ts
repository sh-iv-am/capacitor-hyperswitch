import type {
  ElementEvent,
  PaymentEvent,
  PaymentEventData,
  SubscriptionEvent,
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

export function toPaymentEvent(data: PaymentEventData): PaymentEvent {
  return { eventName: data.type as SubscriptionEvent, payload: data.payload ?? {} };
}

const ELEMENT_EVENTS: ReadonlySet<string> = new Set<ElementEvent>(['ready', 'focus', 'blur']);

function isElementEvent(name: string): name is ElementEvent {
  return ELEMENT_EVENTS.has(name);
}

// Only change and lifecycle handlers are supported.
function eventMatcher(event: string): (e: PaymentEvent) => boolean {
  if (event === 'change') return (e) => !isElementEvent(e.eventName);
  if (!isElementEvent(event)) return () => false;
  return (e) => String(e.eventName) === event;
}

/** Wraps a handler for `on(event, handler)`: lifecycle handlers just fire, with no arguments. */
export function listenerFor(event: string, handler: (event?: PaymentEvent) => void): (e: PaymentEvent) => void {
  const matches = eventMatcher(event);
  const lifecycle = isElementEvent(event);
  return (e) => {
    if (!matches(e)) return;
    if (lifecycle) handler();
    else handler(e);
  };
}
