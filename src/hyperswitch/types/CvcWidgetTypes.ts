import { removeListenerFunction } from '../..';
import { ColorType, Font, Shapes } from './AppearanceTypes';
import { PaymentEventData } from './PaymentTypes';

export interface CvcAppearance {
  colors?: ColorType;
  shapes?: Shapes;
  font?: Pick<Font, 'family' | 'scale'>;
}

export interface CvcWidgetOptions {
  appearance?: CvcAppearance;
  placeholder?: string;
  sdkAuthorization?: string;
}

export interface CvcWidget {
  mount(selector: string, options?: CvcWidgetOptions): void;
  unmount(): void;
  destroy(): void;
  on(event: string, handler?: (data?: PaymentEventData) => void): removeListenerFunction | void;
}
