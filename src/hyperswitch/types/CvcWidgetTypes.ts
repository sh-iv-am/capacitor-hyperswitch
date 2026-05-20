import { removeListenerFunction } from './PaymentElementTypes';
import { ColorType, Font, Shapes } from './AppearanceTypes';
import { PaymentEventData } from './PaymentTypes';
import { Theme } from './PaymentSheetTypes';

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
}

export interface CvcWidget {
  mount(selector: string, options?: CvcWidgetOptions): void;
  unmount(): void;
  destroy(): void;
  on(event: string, handler?: (data?: PaymentEventData) => void): removeListenerFunction | null;
}
