import { create } from 'zustand';

import type { ToolPanel } from './catalog';

export type HubView = 'hub' | ToolPanel;

interface HubState {
  open: boolean;
  view: HubView;
  show(view?: HubView): void;
  setView(view: HubView): void;
  close(): void;
}

export const useHubStore = create<HubState>((set) => ({
  open: false,
  view: 'hub',
  show: (view = 'hub') => set({ open: true, view }),
  setView: (view) => set({ view }),
  close: () => set({ open: false }),
}));
