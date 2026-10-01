import { CustomerSavedPaymentMethodsSession } from './CustomerSavedPaymentMethodsSessionTypes';
import { PaymentSessionConfiguration } from './HyperswitchSessionTypes';
import { PaymentSheetOptions, SavedPaymentMethodsConfiguration } from './PaymentSheetTypes';
import { PaymentEvent, PaymentResult } from './PaymentTypes';

export interface PaymentSession {
  /** Events listed in `options.subscriptionEvents` go to `onChange` while the sheet is presented. */
  presentPaymentSheet(options?: PaymentSheetOptions, onChange?: (event: PaymentEvent) => void): Promise<PaymentResult>;
  getCustomerSavedPaymentMethods(
    options?: SavedPaymentMethodsConfiguration,
  ): Promise<CustomerSavedPaymentMethodsSession>;
  updateIntent(intentResolver: () => Promise<PaymentSessionConfiguration>): Promise<void>;
}
