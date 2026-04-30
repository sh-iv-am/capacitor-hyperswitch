import { registerPlugin } from '@capacitor/core';

import type { CVCWidgetPlugin } from './definitions';

export const cvcWidgetPlugin = registerPlugin<CVCWidgetPlugin>('CVCWidget', {
  web: () => import('./web').then((m) => new m.CvcWidgetWeb()),
});

export * from './definitions';
