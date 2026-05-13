import { hyperswitchPlugin } from './plugin';
import { createElements } from './elements';
import { createInitPaymentSession } from './session';
import type {
  HyperswitchConfiguration,
  HyperswitchSession,
  PaymentSessionConfiguration,
  Elements,
  PaymentSession,
} from './definitions';

function init(config: HyperswitchConfiguration): Promise<HyperswitchSession> {
  const initPromise = hyperswitchPlugin.init(config);
  return new Promise((resolve, _) => {
    resolve({
      async elements(options: PaymentSessionConfiguration): Promise<Elements> {
        await initPromise;
        await hyperswitchPlugin.elements({ elementsOptions: options });
        return createElements(hyperswitchPlugin);
      },
      async initPaymentSession(options: PaymentSessionConfiguration): Promise<PaymentSession> {
        await initPromise;
        await hyperswitchPlugin.initPaymentSession({ paymentSessionOptions: options });
        return createInitPaymentSession(hyperswitchPlugin);
      },
    });
  });
}

export { init as loadHyper };
export const Hyperswitch = { init };
export * from './definitions';
