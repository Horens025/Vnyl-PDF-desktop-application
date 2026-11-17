import { commandRegistry } from '@/commands';

import { cycleTab } from './actions';
import { useWorkspaceStore } from './store';

let registered = false;

export function registerWorkspaceCommands(): void {
  if (registered) return;
  registered = true;
  const hasTabs = () => useWorkspaceStore.getState().tabs.length >= 2;
  commandRegistry.register({
    id: 'tabs.next',
    title: 'Next document',
    category: 'File',
    keybinding: 'Mod+Alt+ArrowRight',
    when: hasTabs,
    run: () => cycleTab(1),
  });
  commandRegistry.register({
    id: 'tabs.prev',
    title: 'Previous document',
    category: 'File',
    keybinding: 'Mod+Alt+ArrowLeft',
    when: hasTabs,
    run: () => cycleTab(-1),
  });
}
