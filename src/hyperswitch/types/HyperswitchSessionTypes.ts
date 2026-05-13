import { Elements } from './ElementsTypes';
import { PaymentSession } from './PaymentSessionTypes';

export interface CustomEndpointConfiguration {
  customEndpoint?: string;
}

export interface OverrideEndpontConfiguration {
  customBackendEndpoint?: string;
  customLoggingEndpoint?: string;
  customAssetEndpoint?: string;
  customSDKConfigEndpoint?: string;
  customAirborneEndpoint?: string;
}

export interface HyperswitchConfiguration {
  publishableKey: string;
  profileId?: string;
  customConfig?: CustomEndpointConfiguration | OverrideEndpontConfiguration;
}

export interface PaymentSessionConfiguration {
  sdkAuthorization: string;
}

export interface HyperswitchSession {
  initPaymentSession(options: PaymentSessionConfiguration): Promise<PaymentSession>;
  elements(options: PaymentSessionConfiguration): Promise<Elements>;
}
