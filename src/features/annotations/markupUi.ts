import { create } from 'zustand';

import { DEFAULT_HIGHLIGHT_OPACITY, MARKUP_SWATCHES, type TextMarkupType } from './types';

const KEY = 'vnyl.markup';

interface Persisted {
  swatch: number;
  opacity: number;
}

function read(): Persisted {
  const fallback = { swatch: 0, opacity: DEFAULT_HIGHLIGHT_OPACITY };
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return fallback;
    const v = JSON.parse(raw) as Partial<Persisted>;
    const swatch = Number.isInteger(v.swatch) ? (v.swatch as number) : 0;
    const opacity = typeof v.opacity === 'number' ? v.opacity : fallback.opacity;
    return {
      swatch: swatch >= 0 && swatch < MARKUP_SWATCHES.length ? swatch : 0,
      opacity: Math.min(1, Math.max(0.1, opacity)),
    };
  } catch {
    return fallback;
  }
}

interface MarkupUiState extends Persisted {
  /** Whether the persistent markup toolbar is showing. */
  toolbarOpen: boolean;
  /** The armed tool: while set, selecting text applies it immediately. */
  tool: TextMarkupType | null;
  toggleToolbar(): void;
  setTool(tool: TextMarkupType | null): void;
  setSwatch(index: number): void;
  setOpacity(opacity: number): void;
}

/** The markup color/opacity the user last picked; remembered across sessions. */
export const useMarkupUi = create<MarkupUiState>((set, get) => {
  const save = () => {
    try {
      const { swatch, opacity } = get();
      localStorage.setItem(KEY, JSON.stringify({ swatch, opacity }));
    } catch {
      /* storage unavailable; the choice lasts for this session */
    }
  };
  return {
    ...read(),
    toolbarOpen: false,
    tool: null,
    // Closing the toolbar also disarms the tool, so a hidden tool can never
    // keep marking up text invisibly.
    toggleToolbar: () => set((s) => ({ toolbarOpen: !s.toolbarOpen, tool: null })),
    setTool: (tool) => set({ tool }),
    setSwatch: (swatch) => {
      set({ swatch });
      save();
    },
    setOpacity: (opacity) => {
      set({ opacity: Math.min(1, Math.max(0.1, opacity)) });
      save();
    },
  };
});
