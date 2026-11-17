import { announce } from '@/a11y/announcer';
import { commandRegistry } from '@/commands';
import { useWorkspaceStore } from '@/features/workspace';
import { useDocumentStore } from '@/state/documentStore';

import { useSplitStore } from './store';

/** Open (or close) split view. Prefer a different open document; otherwise let the user choose. */
export function toggleSplit(): void {
  const split = useSplitStore.getState();
  if (split.open) {
    split.setOpen(false);
    announce('Split view closed');
    return;
  }
  const { tabs, activeId } = useWorkspaceStore.getState();
  const other = tabs.find((t) => t.id !== activeId);
  split.setSource(other ? { kind: 'tab', id: other.id } : null);
  split.setOpen(true);
  announce(other ? `Split view open, showing ${other.name}` : 'Split view open. Choose a document for the second pane');
}

let registered = false;

export function registerSplitCommands(): void {
  if (registered) return;
  registered = true;
  commandRegistry.register({
    id: 'view.toggleSplit',
    title: 'Toggle split view',
    category: 'View',
    keybinding: 'Mod+Alt+S',
    when: () => useDocumentStore.getState().status === 'ready' || useSplitStore.getState().open,
    run: toggleSplit,
  });
}
