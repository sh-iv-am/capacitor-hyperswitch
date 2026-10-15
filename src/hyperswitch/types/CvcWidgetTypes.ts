import { removeListenerFunction } from './PaymentElementTypes';
import { ColorType, Font, Shapes } from './AppearanceTypes';
import { ElementEvent, PaymentEvent } from './PaymentTypes';
import { SubscriptionEvent, Theme } from './PaymentSheetTypes';

export interface CvcAppearance {
  theme?: Theme;
  colors?: ColorType;
  shapes?: Shapes;
  font?: Pick<Font, 'family' | 'scale'>;
}

export interface CvcWidgetOptions {
  appearance?: CvcAppearance;
  placeholder?: string;
  sdkAuthorization?: string;
  cvcIcon?: 'hidden' | 'shown';
  /** Events delivered to `on('change')`; the CVC widget emits `cvcStatusChange`. */
  subscriptionEvents?: SubscriptionEvent[];
}

export interface CvcWidget {
  mount(selector: string, options?: CvcWidgetOptions): void;
  unmount(): void;
  destroy(): void;
  /** Receives every event listed in `subscriptionEvents`; filter on `event.eventName`. */
  on(event: 'change', handler?: (event: PaymentEvent) => void): removeListenerFunction | null;
  /** Lifecycle events; see {@link ElementEvent}. */
  on(event: ElementEvent, handler?: () => void): removeListenerFunction | null;
}
