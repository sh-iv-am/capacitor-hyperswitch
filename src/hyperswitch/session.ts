import type {
  HyperswitchPlugin,
  InitPaymentSession,
  JSONValue,
  PaymentResult,
  PaymentSessionHandler,
} from './definitions';
import { createPaymentSessionHandler } from './payment-session-handler';

export function createInitPaymentSession(plugin: HyperswitchPlugin): InitPaymentSession {
  return {
    async presentPaymentSheet(options?: JSONValue): Promise<PaymentResult> {
      return plugin.presentPaymentSheet({ sheetOptions: options ?? {} });
    },

    async getCustomerSavedPaymentMethods(): Promise<PaymentSessionHandler> {
      const { handlerId } = await plugin.getCustomerSavedPaymentMethods();
      return createPaymentSessionHandler(plugin, handlerId);
    },
  };
}
