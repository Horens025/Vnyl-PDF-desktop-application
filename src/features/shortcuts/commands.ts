import { commandRegistry } from '@/commands';

import { useShortcutsStore } from './store';

let registered = false;

export function registerShortcutsCommands(): void {
  if (registered) return;
  registered = true;
  commandRegistry.register({
    id: 'help.shortcuts',
    title: 'Keyboard shortcuts',
    category: 'Help',
    keybinding: 'Mod+/',
    run: () => useShortcutsStore.getState().show(),
  });
}
