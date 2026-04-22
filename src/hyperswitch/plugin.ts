import { registerPlugin } from '@capacitor/core';

import type { HyperswitchPlugin } from './definitions';

export const hyperswitchPlugin = registerPlugin<HyperswitchPlugin>('Hyperswitch', {
  web: () => import('./web').then((m) => new m.HyperswitchWeb()),
});
