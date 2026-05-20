import type {
  HyperswitchPlugin,
  PaymentSession,
  PaymentResult,
  PaymentSheetOptions,
  CustomerSavedPaymentMethodsSession,
  SavedPaymentMethodsConfiguration,
} from './definitions';
import { createPaymentSessionHandler } from './PaymentSession';

export function createInitPaymentSession(plugin: HyperswitchPlugin): PaymentSession {
  return {
    async presentPaymentSheet(options?: PaymentSheetOptions): Promise<PaymentResult> {
      return plugin.presentPaymentSheet({ sheetOptions: options ?? {} });
    },
    async getCustomerSavedPaymentMethods(
      options?: SavedPaymentMethodsConfiguration,
    ): Promise<CustomerSavedPaymentMethodsSession> {
      const { handlerId } = await plugin.getCustomerSavedPaymentMethods({ configuration: options });
      return createPaymentSessionHandler(plugin, handlerId);
    },
    async updateIntent(intentResolver) {
      const data = await intentResolver();
      return await plugin.updateIntent(data);
    },
  };
}
