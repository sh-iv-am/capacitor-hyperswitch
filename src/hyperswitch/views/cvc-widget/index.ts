import { registerPlugin } from '@capacitor/core';

import type { CvcWidgetPlugin } from './definitions';

export const cvcWidgetPlugin = registerPlugin<CvcWidgetPlugin>('CvcWidget', {
  web: () => import('./web').then((m) => new m.CvcWidgetWeb()),
});

export * from './definitions';
