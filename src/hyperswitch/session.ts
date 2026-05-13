import type {
  HyperswitchPlugin,
  PaymentSession,
  PaymentResult,
  PaymentSheetOptions,
  CustomerSavedPaymentMethodsSession,
} from './definitions';
import { createPaymentSessionHandler } from './PaymentSession';

export function createInitPaymentSession(plugin: HyperswitchPlugin): PaymentSession {
  return {
    async presentPaymentSheet(options?: PaymentSheetOptions): Promise<PaymentResult> {
      return plugin.presentPaymentSheet({ sheetOptions: options ?? {} });
    },
    async getCustomerSavedPaymentMethods(): Promise<CustomerSavedPaymentMethodsSession> {
      const { handlerId } = await plugin.getCustomerSavedPaymentMethods();
      return createPaymentSessionHandler(plugin, handlerId);
    },
    async updateIntent(intentResolver) {
      const data = await intentResolver();
      return await plugin.updateIntent(data);
    },
  };
}
