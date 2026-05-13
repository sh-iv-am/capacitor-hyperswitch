import { CvcWidget, CvcWidgetOptions } from './CvcWidgetTypes';
import { PaymentElement, PaymentElementOptions } from './PaymentElementTypes';
import { PaymentSession } from './PaymentSessionTypes';

export type Elements = Omit<PaymentSession, 'presentPaymentSheet'> & {
  create(options: { type: 'paymentElement'; options?: PaymentElementOptions }): PaymentElement;
  create(options: { type: 'cvcWidget'; options?: CvcWidgetOptions }): CvcWidget;
};
