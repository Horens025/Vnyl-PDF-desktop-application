import { create } from 'zustand';

interface ShortcutsState {
  open: boolean;
  show(): void;
  close(): void;
}

export const useShortcutsStore = create<ShortcutsState>((set) => ({
  open: false,
  show: () => set({ open: true }),
  close: () => set({ open: false }),
}));
