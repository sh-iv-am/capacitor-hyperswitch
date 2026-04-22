export interface CreateOptions {
  x: number;
  y: number;
  width: number;
  height: number;
  color?: string;
}

export interface UpdatePositionOptions {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface PaymentElementPlugin {
  create(options: CreateOptions): Promise<void>;
  destroy(): Promise<void>;
  updatePosition(options: UpdatePositionOptions): Promise<void>;
  show(): Promise<void>;
  hide(): Promise<void>;
}
