import type {
  ElementEvent,
  LegacySubscriptionEvent,
  PaymentEvent,
  PaymentEventData,
  SubscriptionEvent,
} from './definitions';

const LEGACY_EVENT_NAMES: Record<LegacySubscriptionEvent, SubscriptionEvent> = {
  PAYMENT_METHOD_INFO_CARD: 'cardDetailsChange',
  PAYMENT_METHOD_STATUS: 'paymentMethodChange',
  FORM_STATUS: 'formStatusChange',
  PAYMENT_METHOD_INFO_BILLING_ADDRESS: 'billingDetailsChange',
  CVC_STATUS: 'cvcStatusChange',
};

const LEGACY_NAME_OF = (Object.keys(LEGACY_EVENT_NAMES) as LegacySubscriptionEvent[]).reduce<
  Record<string, LegacySubscriptionEvent>
>((acc, legacy) => ({ ...acc, [LEGACY_EVENT_NAMES[legacy]]: legacy }), {});

/** Maps a pre-rename event name to its current name; current names pass through. */
function toSubscriptionEvent(name: string): string {
  return LEGACY_EVENT_NAMES[name as LegacySubscriptionEvent] ?? name;
}

// Folds the deprecated `subscribedEvents` (and legacy names) into `subscriptionEvents` for native.
export function withSubscriptionEvents<T extends { subscriptionEvents?: string[]; subscribedEvents?: string[] }>(
  options: T | undefined,
): T {
  const { subscribedEvents, ...rest } = (options ?? {}) as T;
  const merged = [...(options?.subscriptionEvents ?? []), ...(subscribedEvents ?? [])].map(toSubscriptionEvent);
  const unique = Array.from(new Set(merged));
  const result = { ...rest } as { subscriptionEvents?: string[] };
  if (unique.length > 0) {
    result.subscriptionEvents = unique;
  } else {
    delete result.subscriptionEvents;
  }
  return result as T;
}

// The deprecated `type` keeps the pre-rename name (e.g. `CVC_STATUS`) so old `type` checks still match.
export function toPaymentEvent(data: PaymentEventData): PaymentEvent {
  const eventName = toSubscriptionEvent(data.type) as SubscriptionEvent;
  return { eventName, payload: data.payload ?? {}, type: LEGACY_NAME_OF[eventName] ?? eventName };
}

const ELEMENT_EVENTS: ReadonlySet<string> = new Set<ElementEvent>(['ready', 'focus', 'blur']);

function isElementEvent(name: string): name is ElementEvent {
  return ELEMENT_EVENTS.has(name);
}

// `'change'` matches every event except ready/focus/blur; any other name matches only itself.
function eventMatcher(event: string): (e: PaymentEvent) => boolean {
  if (event === 'change') return (e) => !isElementEvent(e.eventName);
  const wanted = toSubscriptionEvent(event);
  return (e) => e.eventName === wanted;
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
