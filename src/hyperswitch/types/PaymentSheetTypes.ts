import { ColorType, Font, Shapes } from './AppearanceTypes';

export type SubscriptionEvent =
  | 'PAYMENT_METHOD_INFO_CARD'
  | 'PAYMENT_METHOD_STATUS'
  | 'FORM_STATUS'
  | 'PAYMENT_METHOD_INFO_BILLING_ADDRESS'

export type Theme = 'Default' | 'Light' | 'Dark' | 'Minimal' | 'FlatMinimal';
export type LayoutType = 'tabs' | 'accordion';
export type PaymentMethodsArrangement = 'grid' | 'auto';
export type RedirectionInfo = 'hidden' | 'visible';
export type CvcIconDisplay = 'shown' | 'hidden';

export interface GroupingBehavior {
  displayInSeparateScreen?: boolean;
  groupByPaymentMethods?: boolean;
}

export interface SavedMethodCustomization {
  defaultCollapsed?: boolean;
  hideCardExpiry?: boolean;
  hideCVCError?: boolean;
  cvcIcon?: CvcIconDisplay;
  groupingBehavior?: GroupingBehavior;
}

export interface PaymentMethodLayout {
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

export type GooglePayButtonType = 'buy' | 'book' | 'checkout' | 'donate' | 'order' | 'pay' | 'subscribe' | 'plain';
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

export interface WalletButtonsConfiguration {
  googlePay?: GooglePayConfiguration;
  applePay?: ApplePayConfiguration;
}

export interface LogoColors {
  backgroundColor?: string;
  unselected?: string;
}

export interface LogoColorType {
  light?: LogoColors;
  dark?: LogoColors;
}

export interface CheckedIconColors {
  color?: string;
}

export interface CheckedIconColorType {
  light?: CheckedIconColors;
  dark?: CheckedIconColors;
}

export interface CheckedIconForSelection {
  colors?: CheckedIconColorType;
}

export interface LogoCustomization {
  borderRadius?: number;
  colors?: LogoColorType;
  checkedIconForSelection?: CheckedIconForSelection;
}

export interface Appearance {
  theme?: Theme;
  colors?: ColorType;
  shapes?: Shapes;
  font?: Font;
  primaryButton?: PrimaryButton;
  logo?: LogoCustomization;
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
  postalCode?: string;
  state?: string;
}

export interface Phone {
  number?: string;
  code?: string;
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

export interface PaymentMethodConfig {
  paymentMethod: string;
  message?: string;
}

type locale =
  | "en"
  | "he"
  | "fr"
  | "en-GB"
  | "ar"
  | "ja"
  | "de"
  | "fr-BE"
  | "es"
  | "ca"
  | "pt"
  | "it"
  | "pl"
  | "nl"
  | "nI-BE"
  | "sv"
  | "ru"
  | "lt"
  | "cs"
  | "sk"
  | "ls"
  | "cy"
  | "el"
  | "et"
  | "fi"
  | "nb"
  | "bs"
  | "da"
  | "ms"
  | "tr-CY"

export interface PaymentSheetOptions {
  sdkAuthorization?: string;
  allowsDelayedPaymentMethods?: boolean;
  appearance?: Appearance;
  shippingDetails?: AddressDetails;
  primaryButtonLabel?: string;
  paymentSheetHeaderText?: string;
  savedPaymentScreenHeaderText?: string;
  merchantDisplayName?: string;
  billingDetails?: AddressDetails;
  primaryButtonColor?: string;
  allowsPaymentMethodsRequiringShippingAddress?: boolean;
  displaySavedPaymentMethodsCheckbox?: boolean;
  displaySavedPaymentMethods?: boolean;
  displayPayButton?: boolean;
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
  locale?: locale;
  redirectionInfo?: RedirectionInfo;
  stickyPayButton?: boolean;
  paymentMethodLayout?: PaymentMethodLayout;
  walletButtonsConfiguration?: WalletButtonsConfiguration;
  paymentMethodsConfig?: PaymentMethodConfig[];
  paymentMethodOrder?: string[];
}
