import { create } from 'zustand';

import type { FitMode } from '@/state/viewerStore';

/** One open PDF in the tab strip. */
export interface Tab {
  id: string;
  name: string;
  /**
   * The fingerprint per-document data (annotations, signatures, edits, OCR) is
   * stored under. Stays the ORIGINAL fingerprint even after in-place edits, as
   * `reloadEditedBytes` does, so a tab reloaded from edited bytes finds it all.
   */
  fingerprint: string;
  /** This tab's own copy of the file; null for URL-backed documents. */
  data: Uint8Array | null;
  url: string | null;
  path: string | null;
  /** Edited bytes were swapped in, so the document differs from its source file. */
  edited: boolean;
  page: number;
  scale: number;
  fitMode: FitMode;
}

interface WorkspaceState {
  tabs: Tab[];
  activeId: string | null;

  upsert(tab: Tab): void;
  patch(id: string, patch: Partial<Tab>): void;
  remove(id: string): void;
  setActive(id: string | null): void;
  reset(): void;
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  tabs: [],
  activeId: null,

  upsert: (tab) =>
    set((s) => ({
      tabs: s.tabs.some((t) => t.id === tab.id)
        ? s.tabs.map((t) => (t.id === tab.id ? tab : t))
        : [...s.tabs, tab],
    })),
  patch: (id, patch) =>
    set((s) => ({ tabs: s.tabs.map((t) => (t.id === id ? { ...t, ...patch } : t)) })),
  remove: (id) =>
    set((s) => ({
      tabs: s.tabs.filter((t) => t.id !== id),
      activeId: s.activeId === id ? null : s.activeId,
    })),
  setActive: (activeId) => set({ activeId }),
  reset: () => set({ tabs: [], activeId: null }),
}));

/** The tab to show after `index` is closed: its right neighbour, else its left. */
export function neighbourAfterClose(tabs: readonly Tab[], closedId: string): Tab | null {
  const i = tabs.findIndex((t) => t.id === closedId);
  if (i < 0) return tabs[0] ?? null;
  return tabs[i + 1] ?? tabs[i - 1] ?? null;
}
