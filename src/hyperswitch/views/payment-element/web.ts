import { WebPlugin } from '@capacitor/core';

import type { CreateOptions, PaymentElementPlugin, UpdatePositionOptions } from './definitions';

export class PaymentElementWeb extends WebPlugin implements PaymentElementPlugin {
  async create(options: CreateOptions): Promise<void> {
    this.destroy();
    const div = document.createElement('div');
    div.id = '__payment-element-view';
    div.style.position = 'fixed';
    div.style.left = `${options.x}px`;
    div.style.top = `${options.y}px`;
    div.style.width = `${options.width}px`;
    div.style.height = `${options.height}px`;
    div.style.backgroundColor = options.color ?? '#FF0000';
    div.style.zIndex = '99999';
    div.style.pointerEvents = 'none';
    document.body.appendChild(div);
  }

  async destroy(): Promise<void> {
    document.getElementById('__payment-element-view')?.remove();
  }

  async updatePosition(options: UpdatePositionOptions): Promise<void> {
    const el = document.getElementById('__payment-element-view');
    if (el) {
      el.style.left = `${options.x}px`;
      el.style.top = `${options.y}px`;
      el.style.width = `${options.width}px`;
      el.style.height = `${options.height}px`;
    }
  }

  async show(): Promise<void> {
    const el = document.getElementById('__payment-element-view');
    if (el) el.style.display = '';
  }

  async hide(): Promise<void> {
    const el = document.getElementById('__payment-element-view');
    if (el) el.style.display = 'none';
  }
}
