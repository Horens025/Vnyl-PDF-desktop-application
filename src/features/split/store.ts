import { create } from 'zustand';

export type SplitOrientation = 'columns' | 'rows';

/** What the second pane shows: one of the open tabs, or a file opened just for it. */
export type SplitSource =
  { kind: 'tab'; id: string } | { kind: 'file'; name: string; data: Uint8Array };

const KEY = 'vnyl.split';
export const MIN_RATIO = 0.2;
export const MAX_RATIO = 0.8;

export const clampRatio = (r: number) => Math.min(MAX_RATIO, Math.max(MIN_RATIO, r));

interface Persisted {
  orientation: SplitOrientation;
  ratio: number;
  sync: boolean;
}

function read(): Persisted {
  const fallback: Persisted = { orientation: 'columns', ratio: 0.5, sync: false };
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<Persisted> | null;
    if (!v) return fallback;
    return {
      orientation: v.orientation === 'rows' ? 'rows' : 'columns',
      ratio: typeof v.ratio === 'number' ? clampRatio(v.ratio) : fallback.ratio,
      sync: v.sync === true,
    };
  } catch {
    return fallback;
  }
}

interface SplitState extends Persisted {
  open: boolean;
  source: SplitSource | null;

  setOpen(open: boolean): void;
  setSource(source: SplitSource | null): void;
  setOrientation(orientation: SplitOrientation): void;
  setRatio(ratio: number): void;
  setSync(sync: boolean): void;
}

export const useSplitStore = create<SplitState>((set, get) => {
  const save = () => {
    try {
      const { orientation, ratio, sync } = get();
      localStorage.setItem(KEY, JSON.stringify({ orientation, ratio, sync }));
    } catch {
      /* storage unavailable: the layout lasts for this session */
    }
  };
  return {
    ...read(),
    open: false,
    source: null,

    setOpen: (open) => set({ open }),
    setSource: (source) => set({ source }),
    setOrientation: (orientation) => {
      set({ orientation });
      save();
    },
    setRatio: (ratio) => {
      set({ ratio: clampRatio(ratio) });
      save();
    },
    setSync: (sync) => {
      set({ sync });
      save();
    },
  };
});

/** Fraction scrolled (0..1) of a scroller, so two different-length documents can track each other. */
export function scrollFraction(el: {
  scrollTop: number;
  scrollHeight: number;
  clientHeight: number;
}): number {
  const range = el.scrollHeight - el.clientHeight;
  return range > 0 ? Math.min(1, Math.max(0, el.scrollTop / range)) : 0;
}

export function scrollTopForFraction(
  el: { scrollHeight: number; clientHeight: number },
  fraction: number,
): number {
  return Math.max(0, el.scrollHeight - el.clientHeight) * fraction;
}
