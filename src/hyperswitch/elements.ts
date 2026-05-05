import type {
  Elements,
  HyperswitchPlugin,
  JSONValue,
  PaymentElement,
  PaymentEventData,
  PaymentResult,
  UpdateIntentResult,
  CvcWidget,
  PaymentSessionHandler,
  PaymentSheetOptions,
  CvcWidgetOptions,
} from './definitions';
import { paymentElementPlugin } from './views/payment-element/index';
import { cvcWidgetPlugin } from './views/cvc-widget/index';
import { createPaymentSessionHandler } from './payment-session-handler';

function getContentPosition(el: HTMLElement): { x: number; y: number; width: number; height: number } {
  const rect = el.getBoundingClientRect();
  return {
    x: rect.left + window.scrollX,
    y: rect.top + window.scrollY,
    width: rect.width,
    height: rect.height,
  };
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

  function syncNativeView(): void {
    if (!mountedElement) return;
    paymentElementPlugin.updatePosition(getContentPosition(mountedElement));
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
  }

  function stopObserving(): void {
    resizeObserver?.disconnect();
    resizeObserver = null;
    mutationObserver?.disconnect();
    mutationObserver = null;
    intersectionObserver?.disconnect();
    intersectionObserver = null;
  }

  return {
    on(event: string, handler?: (data?: PaymentEventData) => void): void {
      if (!handler) return;
      // Subscribe persistently via Capacitor's addListener.
      // Native side fires "paymentElementEvent" for all widget events; we filter by type here.
      plugin.addListener('paymentElementEvent', (eventData: PaymentEventData) => {
        if (eventData.type === event) {
          handler(eventData);
        }
      });
    },
    collapse(): void {
      plugin.elementCollapse();
    },
    blur(): void {
      plugin.elementBlur();
    },
    update(options: JSONValue): void {
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
      plugin.createElement({ type: 'paymentElement', createOptions: (options as unknown as JSONValue) ?? {} });
      startObserving(el);
      plugin.elementMount({ selector });
    },
    focus(): void {
      plugin.elementFocus();
    },
    clear(): void {
      plugin.elementClear();
    },
    async confirmPayment(options?: { confirmParams?: JSONValue }): Promise<PaymentResult> {
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
    cvcWidgetPlugin.updatePosition(getContentPosition(mountedElement));
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
      plugin.createElement({
        type: 'cvcWidget',
        createOptions: cvcCreateOptions as unknown as JSONValue,
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
    on(event: string, handler?: (data?: PaymentEventData) => void): void {
      if (!handler) return;
      if (!eventHandlers.has(event)) {
        eventHandlers.set(event, []);
      }
      eventHandlers.get(event)?.push(handler);
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

    async updateIntent(intentResolver: () => Promise<string>): Promise<UpdateIntentResult> {
      const sdkAuthorization = await intentResolver();
      return plugin.updateIntent({ sdkAuthorization });
    },

    async getCustomerSavedPaymentMethods(): Promise<PaymentSessionHandler> {
      const { handlerId } = await plugin.getCustomerSavedPaymentMethods();
      return createPaymentSessionHandler(plugin, handlerId);
    },
  };
}
