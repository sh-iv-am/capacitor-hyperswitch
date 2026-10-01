import type {
  HyperswitchPlugin,
  PaymentSession,
  PaymentEvent,
  PaymentEventData,
  PaymentResult,
  PaymentSheetOptions,
  CustomerSavedPaymentMethodsSession,
  SavedPaymentMethodsConfiguration,
} from './definitions';
import { createPaymentSessionHandler } from './PaymentSession';
import { toPaymentEvent, withSubscriptionEvents } from './events';

export function createInitPaymentSession(plugin: HyperswitchPlugin): PaymentSession {
  return {
    async presentPaymentSheet(
      options?: PaymentSheetOptions,
      onChange?: (event: PaymentEvent) => void,
    ): Promise<PaymentResult> {
      // Sheet events arrive on the "paymentEvent" channel, listened to only while this sheet is up.
      const listener = onChange
        ? await plugin.addListener('paymentEvent', (eventData: PaymentEventData) =>
            onChange(toPaymentEvent(eventData)),
          )
        : null;
      try {
        return await plugin.presentPaymentSheet({ sheetOptions: withSubscriptionEvents(options) });
      } finally {
        await listener?.remove();
      }
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
