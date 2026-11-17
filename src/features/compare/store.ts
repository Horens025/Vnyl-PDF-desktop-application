import { create } from 'zustand';

export type CompareMode = 'visual' | 'text';

interface CompareState {
  open: boolean;
  /** Tab ids: A is the original, B the revised one. */
  idA: string | null;
  idB: string | null;
  mode: CompareMode;

  show(idA: string | null, idB: string | null): void;
  close(): void;
  setIds(idA: string | null, idB: string | null): void;
  setMode(mode: CompareMode): void;
}

export const useCompareStore = create<CompareState>((set) => ({
  open: false,
  idA: null,
  idB: null,
  mode: 'visual',

  show: (idA, idB) => set({ open: true, idA, idB }),
  close: () => set({ open: false }),
  setIds: (idA, idB) => set({ idA, idB }),
  setMode: (mode) => set({ mode }),
}));
