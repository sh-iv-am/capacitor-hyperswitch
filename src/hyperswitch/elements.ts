import type {
  Elements,
  HyperswitchPlugin,
  PaymentElement,
  PaymentEventData,
  PaymentResult,
  CvcWidget,
  PaymentSheetOptions,
  CvcWidgetOptions,
  PaymentSessionConfiguration,
  CustomerSavedPaymentMethodsSession,
  PaymentRequestData,
  removeListenerFunction,
  SavedMethodCustomization,
} from './definitions';
import { paymentElementPlugin } from './views/payment-element/index';
import { cvcWidgetPlugin } from './views/cvc-widget/index';
import { createPaymentSessionHandler } from './PaymentSession';

let updateIntentInProgress = false;

function getContentPosition(el: HTMLElement): { x: number; y: number; width: number; height: number } {
  const rect = el.getBoundingClientRect();
  return {
    x: rect.left + window.scrollX,
    y: rect.top + window.scrollY,
    width: rect.width,
    height: rect.height,
  };
}

function shouldHide(el: HTMLElement): boolean {
  const { width, height } = el.getBoundingClientRect();
  const opacity = parseFloat(window.getComputedStyle(el).opacity ?? '1');
  return width === 0 || height === 0 || opacity === 0;
}

function toPaymentResult(eventData: PaymentEventData): PaymentResult {
  const message = eventData.payload['message'];

  switch (eventData.payload['type']) {
    case 'completed':
      return { type: 'completed', message };
    case 'canceled':
      return { type: 'canceled', message };
    case 'failed':
      return { type: 'failed', message };
    default:
      throw new Error(`Invalid payment result type: "${eventData.payload['type']}"`);
  }
}
/** Wire up an IntersectionObserver that shows/hides the native view when the
 *  placeholder enters or leaves the viewport (or is covered by an overlay). */
function observeVisibility(el: HTMLElement, onVisible: () => void, onHidden: () => void): IntersectionObserver {
  const obs = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.intersectionRatio > 0) {
          onVisible();
        } else {
          onHidden();
        }
      }
    },
    { threshold: [0, 0.01] },
  );
  obs.observe(el);
  return obs;
}

export function createPaymentElement(plugin: HyperswitchPlugin, options?: PaymentSheetOptions): PaymentElement {
  let mountedElement: HTMLElement | null = null;
  let resizeObserver: ResizeObserver | null = null;
  let mutationObserver: MutationObserver | null = null;
  let intersectionObserver: IntersectionObserver | null = null;
  let onWindowResize: (() => void) | null = null;

  function syncNativeView(): void {
    if (!mountedElement) return;
    if (shouldHide(mountedElement)) {
      paymentElementPlugin.hide();
    } else {
      paymentElementPlugin.show();
      paymentElementPlugin.updatePosition(getContentPosition(mountedElement));
    }
  }

  function startObserving(el: HTMLElement): void {
    resizeObserver = new ResizeObserver(() => syncNativeView());
    resizeObserver.observe(el);
    mutationObserver = new MutationObserver(() => syncNativeView());
    mutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['style', 'class'],
    });
    intersectionObserver = observeVisibility(
      el,
      () => paymentElementPlugin.show(),
      () => paymentElementPlugin.hide(),
    );
    onWindowResize = () => syncNativeView();
    window.addEventListener('resize', onWindowResize);
  }

  function stopObserving(): void {
    resizeObserver?.disconnect();
    resizeObserver = null;
    mutationObserver?.disconnect();
    mutationObserver = null;
    intersectionObserver?.disconnect();
    intersectionObserver = null;
    if (onWindowResize) {
      window.removeEventListener('resize', onWindowResize);
      onWindowResize = null;
    }
  }

  return {
    on(event: string, handler?: (data?: PaymentEventData) => void): removeListenerFunction {
      if (!handler) return { remove: () => {} };
      // Subscribe persistently via Capacitor's addListener.
      // Native side fires "paymentElementEvent" for all widget events; we filter by type here.
      return plugin.addListener('paymentElementEvent', (eventData: PaymentEventData) => {
        if (eventData.type === event) {
          handler(eventData);
        }
      });
    },
    onPaymentResult(handler?: (data: PaymentResult) => void): removeListenerFunction {
      if (!handler) return { remove: () => {} };
      return plugin.addListener('onPaymentResultEvent', (eventData: PaymentEventData) => {
        handler(toPaymentResult(eventData));
      });
    },
    onPaymentConfirmButtonClick(handler?: (data: PaymentRequestData) => boolean): removeListenerFunction {
      if (!handler) return { remove: () => {} };
      plugin.setPaymentConfirmButtonCallback();
      return plugin.addListener('onPaymentConfirmButtonClickEvent', (eventData: PaymentEventData) => {
        try {
          let data = (eventData.payload as PaymentRequestData) || {};
          data.paymentMethodType = data.paymentMethodType?.toUpperCase() || '';
          const proceed = handler(data) !== false;
          plugin.resolvePaymentConfirmButtonClick({ proceed });
        } catch (e) {
          plugin.resolvePaymentConfirmButtonClick({ proceed: false });
        }
      });
    },
    collapse(): void {
      plugin.elementCollapse();
    },
    blur(): void {
      plugin.elementBlur();
    },
    update(options: Record<string, Object>): void {
      plugin.elementUpdate({ updateOptions: options });
    },
    destroy(): void {
      stopObserving();
      paymentElementPlugin.destroy();
      mountedElement = null;
      plugin.elementDestroy();
    },
    unmount(): void {
      stopObserving();
      paymentElementPlugin.destroy();
      mountedElement = null;
      plugin.elementUnmount();
    },
    mount(selector: string): void {
      const el = document.querySelector(selector) as HTMLElement | null;
      if (!el) {
        console.error(`[Hyperswitch] mount: element not found for selector "${selector}"`);
        return;
      }
      mountedElement = el;
      // Create the native view first, then bind it via createElement
      paymentElementPlugin.create({ ...getContentPosition(el) });
      plugin.createElement({ type: 'paymentElement', createOptions: options ?? {} });
      startObserving(el);
      syncNativeView();
      plugin.elementMount({ selector });
    },
    focus(): void {
      plugin.elementFocus();
    },
    clear(): void {
      plugin.elementClear();
    },
    async confirmPayment(options?: { confirmParams?: Record<string, Object> }): Promise<PaymentResult> {
      return plugin.confirmPayment({ confirmParams: options?.confirmParams ?? {} });
    },
  };
}

export function createCvcWidget(plugin: HyperswitchPlugin, options?: CvcWidgetOptions): CvcWidget {
  let mountedElement: HTMLElement | null = null;
  let resizeObserver: ResizeObserver | null = null;
  let mutationObserver: MutationObserver | null = null;
  let intersectionObserver: IntersectionObserver | null = null;
  let onResize: (() => void) | null = null;
  const eventHandlers: Map<string, Array<(data?: PaymentEventData) => void>> = new Map();

  function syncNativeView(): void {
    if (!mountedElement) return;
    if (shouldHide(mountedElement)) {
      cvcWidgetPlugin.hide();
    } else {
      cvcWidgetPlugin.show();
      cvcWidgetPlugin.updatePosition(getContentPosition(mountedElement));
    }
  }

  function stopObserving(): void {
    resizeObserver?.disconnect();
    resizeObserver = null;
    mutationObserver?.disconnect();
    mutationObserver = null;
    intersectionObserver?.disconnect();
    intersectionObserver = null;
    if (onResize) {
      window.removeEventListener('resize', onResize);
      onResize = null;
    }
  }

  // Set up event listener for CVC widget events
  plugin.addListener('cvcWidgetEvent', (eventData: PaymentEventData) => {
    if (eventData.type === 'CVC_STATUS') {
      const handlers = eventHandlers.get('change');
      handlers?.forEach((handler) => handler(eventData));
    }
  });

  return {
    mount(selector: string, mountOptions?: CvcWidgetOptions): void {
      const el = document.querySelector(selector) as HTMLElement | null;
      if (!el) {
        console.error(`[Hyperswitch] CvcWidget mount: element not found for "${selector}"`);
        return;
      }
      mountedElement = el;

      // Merge options: mount options take precedence over create options
      const mergedOptions: CvcWidgetOptions = {
        ...options,
        ...mountOptions,
      };

      // Create the native view first, then bind it via createElement
      cvcWidgetPlugin.create({ ...getContentPosition(el) });
      const cvcCreateOptions = { ...(mergedOptions as unknown as Record<string, unknown>) };
      if (typeof cvcCreateOptions.placeholder === 'string') {
        cvcCreateOptions.placeholder = { cvv: cvcCreateOptions.placeholder };
      }
      if (typeof cvcCreateOptions.cvcIcon === 'string') {
        cvcCreateOptions.paymentMethodLayout = {
          savedMethodCustomization: {
            cvcIcon: cvcCreateOptions.cvcIcon,
          },
        };
      }
      plugin.createElement({
        type: 'cvcWidget',
        createOptions: cvcCreateOptions,
      });
      resizeObserver = new ResizeObserver(() => syncNativeView());
      resizeObserver.observe(el);
      mutationObserver = new MutationObserver(() => syncNativeView());
      mutationObserver.observe(document.body, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['style', 'class'],
      });
      intersectionObserver = observeVisibility(
        el,
        () => cvcWidgetPlugin.show(),
        () => cvcWidgetPlugin.hide(),
      );
      onResize = () => syncNativeView();
      window.addEventListener('resize', onResize);
      syncNativeView();
      plugin.elementMount({ selector });
    },
    unmount(): void {
      stopObserving();
      cvcWidgetPlugin.destroy();
      mountedElement = null;
    },
    destroy(): void {
      stopObserving();
      cvcWidgetPlugin.destroy();
      mountedElement = null;
    },
    on(event: string, handler?: (data?: PaymentEventData) => void): removeListenerFunction | null {
      if (!handler) return { remove: () => {} };
      if (!eventHandlers.has(event)) {
        eventHandlers.set(event, []);
      }
      eventHandlers.get(event)?.push(handler);
      return {
        remove: () => {
          const handlers = eventHandlers.get(event);
          if (!handlers) return;
          const index = handlers.indexOf(handler);
          if (index !== -1) {
            handlers.splice(index, 1);
          }
        },
      };
    },
  };
}

export function createElements(plugin: HyperswitchPlugin): Elements {
  function create(options: { type: 'paymentElement'; options?: PaymentSheetOptions }): PaymentElement;
  function create(options: { type: 'cvcWidget'; options?: CvcWidgetOptions }): CvcWidget;
  function create(options: {
    type: 'paymentElement' | 'cvcWidget';
    options?: PaymentSheetOptions | CvcWidgetOptions;
  }): PaymentElement | CvcWidget {
    if (options.type === 'cvcWidget') {
      return createCvcWidget(plugin, options.options as CvcWidgetOptions);
    }
    return createPaymentElement(plugin, options.options as PaymentSheetOptions);
  }

  return {
    create,

    async updateIntent(intentResolver: () => Promise<PaymentSessionConfiguration>): Promise<void> {
      if (updateIntentInProgress) {
        throw new Error(
          'updateIntent is already in progress. Please wait for the current update to finish before calling it again.',
        );
      } else {
        updateIntentInProgress = true;
        const paymentSessionConfiguration = await intentResolver();
        let result = plugin.updateIntent(paymentSessionConfiguration).then(() => {
          updateIntentInProgress = false;
        });
        return Promise.resolve(result);
      }
    },

    async getCustomerSavedPaymentMethods(
      options?: SavedMethodCustomization,
    ): Promise<CustomerSavedPaymentMethodsSession> {
      const { handlerId } = await plugin.getCustomerSavedPaymentMethods({ configuration: options });
      return createPaymentSessionHandler(plugin, handlerId);
    },
  };
}
