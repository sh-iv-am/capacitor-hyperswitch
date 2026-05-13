import { ColorType, Font, Shapes } from './AppearanceTypes';

export type SubscriptionEvent =
  | 'PAYMENT_METHOD_INFO_CARD'
  | 'PAYMENT_METHOD_STATUS'
  | 'FORM_STATUS'
  | 'PAYMENT_METHOD_INFO_ADDRESS'
  | 'CVC_STATUS';

export type Theme = 'Default' | 'Light' | 'Dark' | 'Minimal' | 'FlatMinimal';
export type LayoutType = 'tabs' | 'accordion';
export type PaymentMethodsArrangement = 'grid' | 'default';

export interface GroupingBehavior {
  displayInSeparateScreen?: boolean;
  groupByPaymentMethods?: boolean;
}

export interface SavedMethodCustomization {
  groupingBehavior?: GroupingBehavior;
}

export interface Layout {
  type?: LayoutType;
  showOneClickWalletsOnTop?: boolean;
  paymentMethodsArrangementForTabs?: PaymentMethodsArrangement;
  defaultCollapsed?: boolean;
  radios?: boolean;
  spacedAccordionItems?: boolean;
  maxAccordionItems?: number;
  savedMethodCustomization?: SavedMethodCustomization;
}

export interface PrimaryButtonColors {
  background?: string;
  text?: string;
  border?: string;
}

export interface PrimaryButtonColorType {
  light?: PrimaryButtonColors;
  dark?: PrimaryButtonColors;
}

export interface PrimaryButton {
  shapes?: Shapes;
  colors?: PrimaryButtonColorType;
}

export type GooglePayButtonType = 'BUY' | 'BOOK' | 'CHECKOUT' | 'DONATE' | 'ORDER' | 'PAY' | 'SUBSCRIBE' | 'PLAIN';
export type GooglePayButtonStyle = 'light' | 'dark';

export interface GooglePayThemeBaseStyle {
  light?: GooglePayButtonStyle;
  dark?: GooglePayButtonStyle;
}

export interface GooglePayConfiguration {
  buttonType?: GooglePayButtonType;
  buttonStyle?: GooglePayThemeBaseStyle;
}

export type ApplePayButtonType = 'buy' | 'setUp' | 'inStore' | 'donate' | 'checkout' | 'book' | 'subscribe' | 'plain';
export type ApplePayButtonStyle = 'white' | 'whiteOutline' | 'black';

export interface ApplePayThemeBaseStyle {
  light?: ApplePayButtonStyle;
  dark?: ApplePayButtonStyle;
}

export interface ApplePayConfiguration {
  buttonType?: ApplePayButtonType;
  buttonStyle?: ApplePayThemeBaseStyle;
}

export interface Appearance {
  colors?: ColorType;
  shapes?: Shapes;
  font?: Font;
  primaryButton?: PrimaryButton;
  googlePay?: GooglePayConfiguration;
  applePay?: ApplePayConfiguration;
  theme?: Theme;
  layout?: Layout;
  locale?: string;
}

export interface Placeholder {
  cardNumber?: string;
  expiryDate?: string;
  cvv?: string;
}

export interface Address {
  first_name?: string;
  last_name?: string;
  city?: string;
  country?: string;
  line1?: string;
  line2?: string;
  zip?: string;
  state?: string;
}

export interface Phone {
  number?: string;
  country_code?: string;
}

export interface AddressDetails {
  address?: Address;
  email?: string;
  phone?: Phone;
}

export interface CustomerConfiguration {
  id?: string;
  ephemeralKeySecret?: string;
}

export interface PaymentSheetOptions {
  sdkAuthorization?: string;
  allowsDelayedPaymentMethods?: boolean;
  appearance?: Appearance;
  shippingDetails?: AddressDetails;
  primaryButtonLabel?: string;
  paymentSheetHeaderText?: string;
  savedPaymentScreenHeaderText?: string;
  merchantDisplayName?: string;
  defaultBillingDetails?: AddressDetails;
  primaryButtonColor?: string;
  allowsPaymentMethodsRequiringShippingAddress?: boolean;
  displaySavedPaymentMethodsCheckbox?: boolean;
  displaySavedPaymentMethods?: boolean;
  placeholder?: Placeholder;
  defaultView?: boolean;
  disableBranding?: boolean;
  netceteraSDKApiKey?: string;
  displayDefaultSavedPaymentIcon?: boolean;
  enablePartialLoading?: boolean;
  customer?: CustomerConfiguration;
  paymentSheetHeaderLabel?: string;
  savedPaymentSheetHeaderLabel?: string;
  subscribedEvents?: SubscriptionEvent[];
  hideConfirmButton?: boolean;
}
