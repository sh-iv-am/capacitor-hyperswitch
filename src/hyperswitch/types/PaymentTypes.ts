export interface PaymentResult {
  type: 'completed' | 'canceled' | 'failed';
  message?: string;
}

export interface PaymentRequestData {
  paymentMethodType: string;
  [key: string]: any;
}

export interface PaymentEventData {
  type: string;
  payload: Record<string, string>;
}
