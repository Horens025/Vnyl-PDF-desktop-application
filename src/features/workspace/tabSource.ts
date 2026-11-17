import { getEngine, type DocumentSource } from '@/core/pdf';
import { useDocumentStore } from '@/state/documentStore';

import type { Tab } from './store';

/**
 * The bytes to show for a tab in a second engine (split pane, compare). The
 * active tab, if it has been edited, is re-saved so the copy matches what is on
 * screen; every other tab already holds its own up-to-date bytes.
 */
export async function getTabSource(tab: Tab, isActive: boolean): Promise<DocumentSource | null> {
  if (isActive) {
    const doc = useDocumentStore.getState();
    const engine = getEngine();
    if (doc.docVersion > 0 || engine.getPendingEditCount() > 0) {
      try {
        return { kind: 'bytes', data: await engine.saveDocument(), name: tab.name };
      } catch {
        /* fall back to the bytes the tab was opened with */
      }
    }
  }
  if (tab.data) return { kind: 'bytes', data: tab.data.slice(), name: tab.name };
  return tab.url ? { kind: 'url', url: tab.url, name: tab.name } : null;
}
