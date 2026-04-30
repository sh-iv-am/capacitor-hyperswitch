import { WebPlugin } from '@capacitor/core';

import type { CVCWidgetPlugin } from './definitions';

export class CvcWidgetWeb extends WebPlugin implements CVCWidgetPlugin {
  async create(options: { x: number; y: number; width: number; height: number }): Promise<void> {
    console.log('CvcWidget create', options);
  }

  async destroy(): Promise<void> {
    console.log('CvcWidget destroy');
  }

  async updatePosition(options: { x: number; y: number; width: number; height: number }): Promise<void> {
    console.log('CvcWidget updatePosition', options);
  }

  async show(): Promise<void> {
    console.log('CvcWidget show');
  }

  async hide(): Promise<void> {
    console.log('CvcWidget hide');
  }
}
