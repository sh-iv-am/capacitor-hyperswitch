import { registerPlugin } from '@capacitor/core';

import type { PaymentElementPlugin } from './definitions';

export const paymentElementPlugin = registerPlugin<PaymentElementPlugin>('PaymentElement', {
  web: () => import('./web').then((m) => new m.PaymentElementWeb()),
});

export * from './definitions';
