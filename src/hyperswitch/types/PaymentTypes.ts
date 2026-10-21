export interface PaymentResult {
  type: 'completed' | 'canceled' | 'failed';
  message?: string;
}

/** Raw event as it crosses the plugin bridge; for payment events `type` is the event name. */
export interface PaymentEventData {
  type: string;
  payload: Record<string, unknown>;
}

/** Payloads delivered to merchants by the current subscription events. */
export type CardDetailsChange = {
  readonly bin: string | null;
  readonly extendedBin: string | null;
  readonly last4: string | null;
  readonly brand: string | null;
  readonly expiryMonth: string | null;
  readonly expiryYear: string | null;
  readonly formattedExpiry: string | null;
  readonly isCardNumberComplete: boolean;
  readonly isCardNumberValid: boolean;
  readonly isExpiryComplete: boolean;
  readonly isExpiryValid: boolean;
  readonly isCvcComplete: boolean;
};

export type PaymentMethodChange = {
  readonly paymentMethod: string;
  readonly paymentMethodType: string;
  readonly isSavedPaymentMethod: boolean;
  readonly isOneClickWallet: boolean;
};

export type FormStatusValue = 'EMPTY' | 'FILLING' | 'COMPLETE';
export type FormStatusChange = { readonly status: FormStatusValue };

export type BillingDetailsChange = {
  readonly country: string | null;
  readonly state: string | null;
  readonly postalCode: string | null;
};

export type CvcStatusChange = {
  readonly cvcStatus: {
    readonly isCvcEmpty: boolean;
    readonly isCvcComplete: boolean;
  };
};

/** Adding a name here requires its payload to be defined as well. */
export type PaymentEventPayloads = {
  cardDetailsChange: CardDetailsChange;
  paymentMethodChange: PaymentMethodChange;
  formStatusChange: FormStatusChange;
  billingDetailsChange: BillingDetailsChange;
  cvcStatusChange: CvcStatusChange;
};

/** Check eventName to narrow payload without a cast. */
export type PaymentEvent<Name extends keyof PaymentEventPayloads = keyof PaymentEventPayloads> = {
  [Key in Name]: {
    readonly eventName: Key;
    readonly payload: PaymentEventPayloads[Key];
  };
}[Name];

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
