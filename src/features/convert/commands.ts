import { announce } from '@/a11y/announcer';
import { commandRegistry } from '@/commands';

import { useHubStore } from './store';

let registered = false;

export function registerConvertCommands(): void {
  if (registered) return;
  registered = true;
  commandRegistry.register({
    id: 'tools.hub',
    title: 'All tools…',
    category: 'Tools',
    keybinding: 'Mod+Shift+K',
    run: () => {
      useHubStore.getState().show('hub');
      announce('Tools');
    },
  });
}
