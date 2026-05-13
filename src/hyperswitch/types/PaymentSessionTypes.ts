import { CustomerSavedPaymentMethodsSession } from './CustomerSavedPaymentMethodsSessionTypes';
import { PaymentSessionConfiguration } from './HyperswitchSessionTypes';
import { PaymentSheetOptions } from './PaymentSheetTypes';
import { PaymentResult } from './PaymentTypes';

export interface PaymentSession {
  presentPaymentSheet(options?: PaymentSheetOptions): Promise<PaymentResult>;
  getCustomerSavedPaymentMethods(): Promise<CustomerSavedPaymentMethodsSession>;
  updateIntent(intentResolver: () => Promise<PaymentSessionConfiguration>): Promise<void>;
}
