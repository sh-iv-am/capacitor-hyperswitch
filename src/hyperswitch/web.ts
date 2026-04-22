import { WebPlugin } from '@capacitor/core';

import type { HyperConfig, HyperswitchPlugin, JSONValue, PaymentResult, UpdateIntentResult } from './definitions';

let defaultProps = {
  from: 'capacitor',
  type: 'payment',
  publishableKey: '',
  sdkAuthorization: '',
  configuration: {
    merchantDisplayName: 'abc',
    paymentSheetHeaderLabel: 'Run on actual device for experience',
    savedPaymentSheetHeaderLabel: 'Run on actual device for experience',
  },
};

export class HyperswitchWeb extends WebPlugin implements HyperswitchPlugin {
  async init(config: HyperConfig): Promise<void> {
    console.log('INIT', config);
    defaultProps.publishableKey = config.publishableKey;
  }

  async elements(options: { elementsOptions: JSONValue }): Promise<{ handlerId: string }> {
    console.log('ELEMENTS', options);
    return { handlerId: 'web-stub' };
  }

  async createElement(options: { type: string; createOptions: JSONValue }): Promise<void> {
    console.log('CREATE_ELEMENT', options);
  }

  async updateIntent(options: { sdkAuthorization: string }): Promise<UpdateIntentResult> {
    console.log('UPDATE_INTENT', options);
    return { type: 'success' };
  }

  async initPaymentSession(options: { paymentSessionOptions: JSONValue }): Promise<void> {
    console.log('INIT_PAYMENT_SESSION', options);
    defaultProps.sdkAuthorization = options.paymentSessionOptions['sdkAuthorization'] as string;
  }

  async getCustomerSavedPaymentMethods(): Promise<{ handlerId: string }> {
    console.log('GET_CUSTOMER_SAVED_PAYMENT_METHODS');
    // Web stub: return a sentinel handlerId; handler methods will log and no-op.
    return { handlerId: 'web-stub' };
  }

  async getCustomerSavedPaymentMethodData(options: { handlerId: string }): Promise<JSONValue> {
    console.log('GET_CUSTOMER_SAVED_PAYMENT_METHOD_DATA', options);
    return {};
  }

  async getCustomerDefaultSavedPaymentMethodData(options: { handlerId: string }): Promise<JSONValue> {
    console.log('GET_CUSTOMER_DEFAULT_SAVED_PAYMENT_METHOD_DATA', options);
    return {};
  }

  async getCustomerLastUsedPaymentMethodData(options: { handlerId: string }): Promise<JSONValue> {
    console.log('GET_CUSTOMER_LAST_USED_PAYMENT_METHOD_DATA', options);
    return {};
  }

  async confirmWithCustomerDefaultPaymentMethod(options: { handlerId: string }): Promise<PaymentResult> {
    console.log('CONFIRM_WITH_CUSTOMER_DEFAULT_PAYMENT_METHOD', options);
    return { type: 'failed', message: 'Run on actual device' };
  }

  async confirmWithCustomerLastUsedPaymentMethod(options: { handlerId: string }): Promise<PaymentResult> {
    console.log('CONFIRM_WITH_CUSTOMER_LAST_USED_PAYMENT_METHOD', options);
    return { type: 'failed', message: 'Run on actual device' };
  }

  async presentPaymentSheet(options: { sheetOptions: JSONValue }): Promise<PaymentResult> {
    console.log('PRESENT_PAYMENT_SHEET', options);

    const CONTAINER_ID = 'hyperswitch-container';
    const IFRAME_ID = 'hyperswitch-mobile-iframe';
    const IFRAME_SRC = 'https://beta.hyperswitch.io/mobile/1.10.0/index.html';
    const TARGET_ORIGIN = 'https://beta.hyperswitch.io';

    const oldContainer = document.getElementById(CONTAINER_ID);
    if (oldContainer) oldContainer.remove();

    const container = document.createElement('div');
    container.id = CONTAINER_ID;
    container.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;z-index:99999;';
    document.body.appendChild(container);

    const iframe = document.createElement('iframe');
    iframe.id = IFRAME_ID;
    iframe.style.cssText = 'width:100%;height:100%;border:none;';
    container.appendChild(iframe);
    iframe.src = IFRAME_SRC;

    return new Promise<PaymentResult>((resolve, reject) => {
      let settled = false;

      const cleanup = () => {
        window.removeEventListener('message', onMessage);
        container.style.display = 'none';
      };

      const settle = (fn: () => void) => {
        if (settled) return;
        settled = true;
        cleanup();
        fn();
      };

      const sendMessage = () => {
        if (!iframe.contentWindow) return;
        iframe.contentWindow.postMessage(JSON.stringify({ initialProps: { props: defaultProps } }), TARGET_ORIGIN);
      };

      const onMessage = (event: MessageEvent) => {
        if (event.origin !== TARGET_ORIGIN) return;
        try {
          const data = JSON.parse(event.data);
          if (data.sdkLoaded) sendMessage();
          if (data.status) {
            let type: PaymentResult['type'];
            switch (data.status) {
              case 'cancelled':
                type = 'canceled';
                break;
              case 'failed':
                type = 'failed';
                break;
              default:
                type = 'completed';
            }
            settle(() => resolve({ type, message: data.message }));
          }
        } catch (error) {
          reject(error);
        }
      };

      window.addEventListener('message', onMessage);
    });
  }

  async confirmPayment(options: { confirmParams: JSONValue }): Promise<PaymentResult> {
    console.log('CONFIRM_PAYMENT', options);
    return { type: 'failed', message: 'Run on actual device' };
  }

  async elementOn(options: { event: string }): Promise<void> {
    console.log('ELEMENT_ON', options);
  }

  async elementCollapse(): Promise<void> {
    console.log('ELEMENT_COLLAPSE');
  }
  async elementBlur(): Promise<void> {
    console.log('ELEMENT_BLUR');
  }

  async elementUpdate(options: { updateOptions: JSONValue }): Promise<void> {
    console.log('ELEMENT_UPDATE', options);
  }

  async elementDestroy(): Promise<void> {
    console.log('ELEMENT_DESTROY');
  }
  async elementUnmount(): Promise<void> {
    console.log('ELEMENT_UNMOUNT');
  }

  async elementMount(options: { selector: string }): Promise<void> {
    console.log('ELEMENT_MOUNT', options);
  }

  async elementFocus(): Promise<void> {
    console.log('ELEMENT_FOCUS');
  }
  async elementClear(): Promise<void> {
    console.log('ELEMENT_CLEAR');
  }

  // addListener is provided by WebPlugin base class; this override satisfies the
  // HyperswitchPlugin interface typing for the 'paymentEvent' event.
  addListener(
    event: 'paymentEvent',
    handler: (data: import('./definitions').PaymentEventData) => void,
  ): Promise<{ remove: () => Promise<void> }> {
    return super.addListener(event, handler);
  }
}
