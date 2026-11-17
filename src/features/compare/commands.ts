import { announce } from '@/a11y/announcer';
import { commandRegistry } from '@/commands';
import { pushToast } from '@/components/common';
import { useWorkspaceStore } from '@/features/workspace';

import { useCompareStore } from './store';

/** Open the comparison on the active document (B) and the next other one (A). */
export function openCompare(): void {
  const { tabs, activeId } = useWorkspaceStore.getState();
  if (tabs.length < 2) {
    pushToast('Open a second document to compare it with this one', 'info');
    return;
  }
  const b = tabs.find((t) => t.id === activeId) ?? tabs[1];
  const a = tabs.find((t) => t.id !== b.id) ?? tabs[0];
  useCompareStore.getState().show(a.id, b.id);
  announce('Compare documents');
}

let registered = false;

export function registerCompareCommands(): void {
  if (registered) return;
  registered = true;
  commandRegistry.register({
    id: 'tools.compare',
    title: 'Compare documents…',
    category: 'Tools',
    when: () => useWorkspaceStore.getState().tabs.length >= 2,
    run: openCompare,
  });
}
