import { onDocumentLoaded } from '@/state/documentHooks';

import { useRecentsStore } from './store';

export { RecentList } from './RecentList';
export { openRecent } from './openRecent';
export { useRecentsStore } from './store';

let registered = false;

/** Remember every document opened from a file on disk (desktop), so it can be reopened from the start screen. */
export function registerRecents(): void {
  if (registered) return;
  registered = true;
  onDocumentLoaded((e) => {
    if (e.path) useRecentsStore.getState().add(e.path, e.info.name);
  });
}
