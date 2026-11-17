import { isTextMarkup, type Annotation } from './types';

/**
 * The text-markup annotation under a point on a page, if any. `x`/`y` are page
 * fractions (0..1), like the stored rects. When markups overlap, the most
 * recently created wins, which is the one drawn on top. `pad` forgives a click
 * that lands a hair outside a thin underline.
 */
export function hitMarkup(
  annotations: readonly Annotation[],
  pageNumber: number,
  x: number,
  y: number,
  pad = 0.002,
): Annotation | null {
  let best: Annotation | null = null;
  for (const a of annotations) {
    if (a.pageNumber !== pageNumber || !isTextMarkup(a.type)) continue;
    const hit = a.rects.some(
      (r) =>
        x >= r.x - pad && x <= r.x + r.width + pad && y >= r.y - pad && y <= r.y + r.height + pad,
    );
    if (hit && (!best || a.createdAt >= best.createdAt)) best = a;
  }
  return best;
}
