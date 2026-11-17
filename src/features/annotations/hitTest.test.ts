import { describe, expect, it } from 'vitest';

import { hitMarkup } from './hitTest';
import type { Annotation } from './types';

const make = (id: string, over: Partial<Annotation> = {}): Annotation => ({
  id,
  type: 'highlight',
  pageNumber: 1,
  color: 'red',
  rects: [{ x: 0.1, y: 0.1, width: 0.4, height: 0.02 }],
  createdAt: 1,
  ...over,
});

describe('hitMarkup', () => {
  it('finds a markup under the point and misses outside it', () => {
    const list = [make('a')];
    expect(hitMarkup(list, 1, 0.3, 0.11)?.id).toBe('a');
    expect(hitMarkup(list, 1, 0.3, 0.5)).toBeNull();
  });

  it('forgives a click a hair outside a thin line', () => {
    expect(hitMarkup([make('a')], 1, 0.3, 0.121)?.id).toBe('a');
  });

  it('only matches the same page and ignores notes', () => {
    const list = [make('a', { pageNumber: 2 }), make('n', { type: 'note' })];
    expect(hitMarkup(list, 1, 0.3, 0.11)).toBeNull();
    expect(hitMarkup(list, 2, 0.3, 0.11)?.id).toBe('a');
  });

  it('prefers the most recently created when markups overlap', () => {
    const list = [make('old', { createdAt: 1 }), make('new', { createdAt: 5 })];
    expect(hitMarkup(list, 1, 0.3, 0.11)?.id).toBe('new');
  });
});
