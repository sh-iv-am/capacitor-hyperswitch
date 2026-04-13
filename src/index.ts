import { registerPlugin } from '@capacitor/core';

import type { HyperswitchPlugin } from './definitions';

const Hyperswitch = registerPlugin<HyperswitchPlugin>('Hyperswitch', {
  web: () => import('./web').then((m) => new m.HyperswitchWeb()),
});

export * from './definitions';
export { Hyperswitch };
