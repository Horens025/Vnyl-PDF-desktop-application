import { describe, expect, it } from 'vitest';

import { CATALOG, filterCatalog, findTool } from './catalog';

describe('tool catalog', () => {
  it('has unique ids and every tool can actually be launched', () => {
    const ids = CATALOG.flatMap((c) => c.tools.map((t) => t.id));
    expect(new Set(ids).size).toBe(ids.length);
    for (const t of CATALOG.flatMap((c) => c.tools)) {
      expect(Boolean(t.panel) !== Boolean(t.command)).toBe(true);
    }
  });

  it('every panel id is also a tool id, which is how the hub finds the tool to show', () => {
    for (const t of CATALOG.flatMap((c) => c.tools)) {
      if (t.panel) expect(findTool(t.panel)?.id).toBe(t.id);
    }
  });

  it('finds a tool by id', () => {
    expect(findTool('pdf-to-images')?.panel).toBe('pdf-to-images');
    expect(findTool('nope')).toBeUndefined();
  });

  it('filters by title or description, case-insensitively, dropping empty groups', () => {
    const groups = filterCatalog('MARKDOWN');
    expect(groups).toHaveLength(1);
    expect(groups[0].tools.map((t) => t.id)).toEqual(['pdf-to-text']);
    expect(filterCatalog('   ')).toBe(CATALOG);
    expect(filterCatalog('zzzz')).toEqual([]);
  });
});
