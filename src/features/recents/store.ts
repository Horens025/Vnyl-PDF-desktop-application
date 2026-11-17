import { create } from 'zustand';

/** A file the desktop app opened from disk, so it can be reopened in one click. */
export interface RecentFile {
  path: string;
  name: string;
  openedAt: number;
}

const KEY = 'vnyl.recents';
export const MAX_RECENTS = 8;

function read(): RecentFile[] {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) ?? '[]') as unknown;
    if (!Array.isArray(raw)) return [];
    return raw
      .filter(
        (r): r is RecentFile =>
          !!r &&
          typeof r.path === 'string' &&
          typeof r.name === 'string' &&
          typeof r.openedAt === 'number',
      )
      .slice(0, MAX_RECENTS);
  } catch {
    return [];
  }
}

interface RecentsState {
  items: RecentFile[];
  add(path: string, name: string, now?: number): void;
  remove(path: string): void;
  clear(): void;
}

export const useRecentsStore = create<RecentsState>((set, get) => {
  const save = () => {
    try {
      localStorage.setItem(KEY, JSON.stringify(get().items));
    } catch {
      /* storage unavailable: the list lasts for this session */
    }
  };
  return {
    items: read(),
    // Most recent first; reopening a file moves it to the top instead of duplicating it.
    add: (path, name, now = Date.now()) => {
      set((s) => ({
        items: [{ path, name, openedAt: now }, ...s.items.filter((r) => r.path !== path)].slice(
          0,
          MAX_RECENTS,
        ),
      }));
      save();
    },
    remove: (path) => {
      set((s) => ({ items: s.items.filter((r) => r.path !== path) }));
      save();
    },
    clear: () => {
      set({ items: [] });
      save();
    },
  };
});

/** "just now", "5 min ago", "yesterday", "3 days ago", else a short date. */
export function timeAgo(then: number, now: number = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - then) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days === 1) return 'yesterday';
  if (days < 7) return `${days} days ago`;
  return new Date(then).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

/** The folder part of a path, for showing under the file name. Handles / and \ separators. */
export function folderOf(path: string): string {
  const cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
  return cut > 0 ? path.slice(0, cut) : '';
}
