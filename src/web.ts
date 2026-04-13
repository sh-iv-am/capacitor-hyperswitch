import { WebPlugin } from '@capacitor/core';

import type { HyperswitchPlugin } from './definitions';

export class HyperswitchWeb extends WebPlugin implements HyperswitchPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
