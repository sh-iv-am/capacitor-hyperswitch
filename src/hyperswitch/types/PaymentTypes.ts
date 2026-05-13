export interface PaymentResult {
  type: 'completed' | 'canceled' | 'failed';
  message?: string;
}

export interface PaymentEventData {
  type: string;
  payload: Record<string, string>;
}
