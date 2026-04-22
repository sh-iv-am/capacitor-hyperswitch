import type { HyperswitchPlugin, JSONValue, PaymentResult, PaymentSessionHandler } from './definitions';

/**
 * Creates a JS-side PaymentSessionHandler wrapper.
 *
 * The `handlerId` is an opaque string returned by the native bridge after
 * `getCustomerSavedPaymentMethods()` is called. Every subsequent call passes
 * this ID back so the native side can route it to the correct stored handler
 * instance — enabling multiple concurrent sessions safely.
 */
export function createPaymentSessionHandler(plugin: HyperswitchPlugin, handlerId: string): PaymentSessionHandler {
  return {
    handlerId,

    getCustomerSavedPaymentMethodData(): Promise<JSONValue> {
      return plugin.getCustomerSavedPaymentMethodData({ handlerId });
    },

    getCustomerDefaultSavedPaymentMethodData(): Promise<JSONValue> {
      return plugin.getCustomerDefaultSavedPaymentMethodData({ handlerId });
    },

    getCustomerLastUsedPaymentMethodData(): Promise<JSONValue> {
      return plugin.getCustomerLastUsedPaymentMethodData({ handlerId });
    },

    confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult> {
      return plugin.confirmWithCustomerDefaultPaymentMethod({ handlerId });
    },

    confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult> {
      return plugin.confirmWithCustomerLastUsedPaymentMethod({ handlerId });
    },
  };
}
