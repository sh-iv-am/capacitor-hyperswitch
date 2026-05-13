import type {
  CustomerSavedPaymentMethodsSession,
  CustomerLastUsedPaymentMethod,
  CustomerDefaultSavedPaymentMethod,
  HyperswitchPlugin,
  PaymentResult,
} from './definitions';

/**
 * Creates a JS-side PaymentSessionHandler wrapper.
 *
 * The `handlerId` is an opaque string returned by the native bridge after
 * `getCustomerSavedPaymentMethods()` is called. Every subsequent call passes
 * this ID back so the native side can route it to the correct stored handler
 * instance — enabling multiple concurrent sessions safely.
 */
export function createPaymentSessionHandler(
  plugin: HyperswitchPlugin,
  handlerId: string,
): CustomerSavedPaymentMethodsSession {
  return {
    getCustomerDefaultSavedPaymentMethodData(): Promise<CustomerDefaultSavedPaymentMethod | null> {
      return new Promise(async (resolve, reject) => {
        let result = await plugin.getCustomerDefaultSavedPaymentMethodData({ handlerId });
        let data = result.data;
        if (data) resolve({...data, payment_method: data.payment_method_str ?? data.payment_method});
        else reject(result);
      });
    },

    getCustomerLastUsedPaymentMethodData(): Promise<CustomerLastUsedPaymentMethod | null> {
      return new Promise(async (resolve, reject) => {
        let result = await plugin.getCustomerLastUsedPaymentMethodData({ handlerId });
        let data = result.data;
        if (data) resolve({...data, payment_method: data.payment_method_str ?? data.payment_method});
        else reject(result);
      });
    },

    confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult> {
      return plugin.confirmWithCustomerDefaultPaymentMethod({ handlerId });
    },

    confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult> {
      return plugin.confirmWithCustomerLastUsedPaymentMethod({ handlerId });
    },
  };
}
