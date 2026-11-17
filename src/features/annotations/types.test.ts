import { describe, expect, it } from 'vitest';

import { isTextMarkup, markupColor } from './types';

describe('markup types', () => {
  it('only a highlight is translucent', () => {
    expect(markupColor('highlight', [255, 214, 10])).toBe('rgba(255, 214, 10, 0.45)');
    expect(markupColor('underline', [255, 214, 10], 0.2)).toBe('rgba(255, 214, 10, 1)');
  });

  it('clamps highlight opacity', () => {
    expect(markupColor('highlight', [0, 0, 0], 5)).toBe('rgba(0, 0, 0, 1)');
    expect(markupColor('highlight', [0, 0, 0], 0)).toBe('rgba(0, 0, 0, 0.1)');
  });

  it('classifies text markup vs note', () => {
    expect(isTextMarkup('squiggly')).toBe(true);
    expect(isTextMarkup('note')).toBe(false);
  });
});
