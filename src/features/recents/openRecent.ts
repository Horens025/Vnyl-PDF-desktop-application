import { announce } from '@/a11y/announcer';
import { pushToast } from '@/components/common';
import { readPath } from '@/core/document/openDocument';
import { loadSource } from '@/state/actions';

import { useRecentsStore, type RecentFile } from './store';

/** Reopen a recent file. A file that has moved or been deleted is dropped from the list, with a note. */
export async function openRecent(file: RecentFile): Promise<void> {
  try {
    await loadSource(await readPath(file.path));
  } catch {
    useRecentsStore.getState().remove(file.path);
    pushToast(`Could not open ${file.name}. It may have been moved or deleted.`, 'error');
    announce(`Could not open ${file.name}`, true);
  }
}
