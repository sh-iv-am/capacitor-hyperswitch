export interface CvcWidgetPlugin {
  create(options: { x: number; y: number; width: number; height: number }): Promise<void>;
  destroy(): Promise<void>;
  updatePosition(options: { x: number; y: number; width: number; height: number }): Promise<void>;
  show(): Promise<void>;
  hide(): Promise<void>;
}
