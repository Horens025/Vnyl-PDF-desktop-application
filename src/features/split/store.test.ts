import { beforeEach, describe, expect, it } from 'vitest';

import {
  clampRatio,
  MAX_RATIO,
  MIN_RATIO,
  scrollFraction,
  scrollTopForFraction,
  useSplitStore,
} from './store';

describe('split store', () => {
  beforeEach(() => {
    localStorage.clear();
    useSplitStore.setState({
      open: false,
      source: null,
      orientation: 'columns',
      ratio: 0.5,
      sync: false,
    });
  });

  it('clamps the pane ratio so neither view can be dragged away', () => {
    expect(clampRatio(0)).toBe(MIN_RATIO);
    expect(clampRatio(5)).toBe(MAX_RATIO);
    expect(clampRatio(0.4)).toBe(0.4);
    useSplitStore.getState().setRatio(0.99);
    expect(useSplitStore.getState().ratio).toBe(MAX_RATIO);
  });

  it('persists layout choices but not whether the pane is open', () => {
    const s = useSplitStore.getState();
    s.setOrientation('rows');
    s.setSync(true);
    s.setOpen(true);
    expect(JSON.parse(localStorage.getItem('vnyl.split')!)).toEqual({
      orientation: 'rows',
      ratio: 0.5,
      sync: true,
    });
  });
});

describe('linked scrolling maths', () => {
  it('maps how far down one scroller is onto another of a different length', () => {
    const long = { scrollTop: 500, scrollHeight: 2000, clientHeight: 500 };
    const short = { scrollHeight: 1000, clientHeight: 400 };
    expect(scrollFraction(long)).toBeCloseTo(1 / 3);
    expect(scrollTopForFraction(short, scrollFraction(long))).toBeCloseTo(200);
  });

  it('is safe when nothing scrolls', () => {
    expect(scrollFraction({ scrollTop: 0, scrollHeight: 400, clientHeight: 400 })).toBe(0);
    expect(scrollTopForFraction({ scrollHeight: 300, clientHeight: 400 }, 0.5)).toBe(0);
  });
});
