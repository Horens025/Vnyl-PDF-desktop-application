import { describe, expect, it } from 'vitest';

import { diffPixels, diffStats, diffWords, type Pixels } from './diff';

describe('diffWords', () => {
  it('reports identical text as one equal run', () => {
    expect(diffWords('the quick fox', 'the  quick\nfox')).toEqual([
      { type: 'equal', text: 'the quick fox' },
    ]);
  });

  it('finds a replaced word', () => {
    const ops = diffWords('the quick brown fox', 'the quick red fox');
    expect(ops).toEqual([
      { type: 'equal', text: 'the quick' },
      { type: 'remove', text: 'brown' },
      { type: 'add', text: 'red' },
      { type: 'equal', text: 'fox' },
    ]);
    expect(diffStats(ops)).toEqual({ added: 1, removed: 1 });
  });

  it('finds inserted and deleted runs', () => {
    const ops = diffWords('alpha beta gamma', 'alpha beta new words gamma');
    expect(ops.filter((o) => o.type === 'add')).toEqual([{ type: 'add', text: 'new words' }]);
    expect(diffStats(diffWords('a b c d', 'a d'))).toEqual({ added: 0, removed: 2 });
  });

  it('handles empty sides', () => {
    expect(diffWords('', 'new text')).toEqual([{ type: 'add', text: 'new text' }]);
    expect(diffWords('old text', '')).toEqual([{ type: 'remove', text: 'old text' }]);
    expect(diffWords('', '')).toEqual([]);
  });

  it('degrades to a coarse replace instead of hanging on huge changes', () => {
    const a = Array.from({ length: 3000 }, (_, i) => `a${i}`).join(' ');
    const b = Array.from({ length: 3000 }, (_, i) => `b${i}`).join(' ');
    const ops = diffWords(a, b);
    expect(ops.map((o) => o.type)).toEqual(['remove', 'add']);
  });
});

const solid = (w: number, h: number, v: number): Pixels => {
  const data = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < data.length; i += 4) data.set([v, v, v, 255], i);
  return { width: w, height: h, data };
};

describe('diffPixels', () => {
  it('sees no change between identical renders', () => {
    const r = diffPixels(solid(4, 4, 255), solid(4, 4, 255));
    expect(r.changed).toBe(0);
    expect(r.ratio).toBe(0);
  });

  it('marks ink only in A red and ink only in B green', () => {
    const a = solid(2, 1, 255);
    const b = solid(2, 1, 255);
    a.data.set([0, 0, 0, 255], 0); // pixel 0 dark in A only
    b.data.set([0, 0, 0, 255], 4); // pixel 1 dark in B only
    const r = diffPixels(a, b);
    expect(r.changed).toBe(2);
    expect([...r.data.slice(0, 3)]).toEqual([230, 57, 70]);
    expect([...r.data.slice(4, 7)]).toEqual([40, 180, 90]);
  });

  it('ignores tiny anti-aliasing differences', () => {
    expect(diffPixels(solid(3, 3, 200), solid(3, 3, 215)).changed).toBe(0);
  });

  it('refuses mismatched sizes', () => {
    expect(() => diffPixels(solid(2, 2, 0), solid(3, 2, 0))).toThrow();
  });
});
