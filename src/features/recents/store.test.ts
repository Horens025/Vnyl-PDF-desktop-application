import { beforeEach, describe, expect, it } from 'vitest';

import { folderOf, MAX_RECENTS, timeAgo, useRecentsStore } from './store';

beforeEach(() => {
  localStorage.clear();
  useRecentsStore.setState({ items: [] });
});

describe('recents store', () => {
  it('puts the newest first and moves a reopened file to the top', () => {
    const s = useRecentsStore.getState();
    s.add('/a.pdf', 'a.pdf', 1);
    s.add('/b.pdf', 'b.pdf', 2);
    s.add('/a.pdf', 'a.pdf', 3);
    expect(useRecentsStore.getState().items.map((r) => r.path)).toEqual(['/a.pdf', '/b.pdf']);
    expect(useRecentsStore.getState().items[0].openedAt).toBe(3);
  });

  it('keeps at most MAX_RECENTS and persists', () => {
    for (let i = 0; i < MAX_RECENTS + 3; i++)
      useRecentsStore.getState().add(`/f${i}.pdf`, `f${i}.pdf`, i);
    expect(useRecentsStore.getState().items).toHaveLength(MAX_RECENTS);
    expect(JSON.parse(localStorage.getItem('vnyl.recents')!)).toHaveLength(MAX_RECENTS);
  });

  it('removes one and clears all', () => {
    const s = useRecentsStore.getState();
    s.add('/a.pdf', 'a.pdf', 1);
    s.add('/b.pdf', 'b.pdf', 2);
    s.remove('/a.pdf');
    expect(useRecentsStore.getState().items.map((r) => r.path)).toEqual(['/b.pdf']);
    s.clear();
    expect(useRecentsStore.getState().items).toEqual([]);
  });
});

describe('formatting', () => {
  const now = Date.UTC(2026, 5, 15, 12, 0, 0);
  it('says how long ago in plain words', () => {
    expect(timeAgo(now - 20_000, now)).toBe('just now');
    expect(timeAgo(now - 5 * 60_000, now)).toBe('5 min ago');
    expect(timeAgo(now - 3 * 3_600_000, now)).toBe('3 h ago');
    expect(timeAgo(now - 30 * 3_600_000, now)).toBe('yesterday');
    expect(timeAgo(now - 4 * 86_400_000, now)).toBe('4 days ago');
  });

  it('finds the folder in Windows and Unix paths', () => {
    expect(folderOf('C:\\Users\\me\\Docs\\a.pdf')).toBe('C:\\Users\\me\\Docs');
    expect(folderOf('/home/me/a.pdf')).toBe('/home/me');
    expect(folderOf('a.pdf')).toBe('');
  });
});
