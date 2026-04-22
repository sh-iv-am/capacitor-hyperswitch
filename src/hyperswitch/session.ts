import type { HyperswitchPlugin, InitPaymentSession, JSONValue, PaymentResult } from './definitions';

export function createInitPaymentSession(plugin: HyperswitchPlugin): InitPaymentSession {
  return {
    async presentPaymentSheet(options?: JSONValue): Promise<PaymentResult> {
      return plugin.presentPaymentSheet({ sheetOptions: options ?? {} });
    },
    async getCustomerSavedPaymentMethods(): Promise<JSONValue> {
      return plugin.getCustomerSavedPaymentMethods();
    },
    async getCustomerDefaultSavedPaymentMethodData(): Promise<JSONValue> {
      return plugin.getCustomerDefaultSavedPaymentMethodData();
    },
    async getCustomerLastUsedPaymentMethodData(): Promise<JSONValue> {
      return plugin.getCustomerLastUsedPaymentMethodData();
    },
    async confirmWithCustomerDefaultPaymentMethod(): Promise<PaymentResult> {
      return plugin.confirmWithCustomerDefaultPaymentMethod();
    },
    async confirmWithCustomerLastUsedPaymentMethod(): Promise<PaymentResult> {
      return plugin.confirmWithCustomerLastUsedPaymentMethod();
    },
  };
}
