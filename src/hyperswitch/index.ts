import { hyperswitchPlugin } from './plugin';
import { createElements } from './elements';
import { createInitPaymentSession } from './session';
import type { HyperConfig, HyperswitchSession, Elements, InitPaymentSession } from './definitions';

function init(config: HyperConfig): HyperswitchSession {
  const initPromise = hyperswitchPlugin.init(config);

  return {
    async elements(options: { sdkAuthorization: string }): Promise<Elements> {
      await initPromise;
      await hyperswitchPlugin.elements({ elementsOptions: options });
      return createElements(hyperswitchPlugin);
    },
    async initPaymentSession(options: { sdkAuthorization: string }): Promise<InitPaymentSession> {
      await initPromise;
      await hyperswitchPlugin.initPaymentSession({ paymentSessionOptions: options });
      return createInitPaymentSession(hyperswitchPlugin);
    },
  };
}

export const Hyperswitch = { init };
export * from './definitions';
