import type { PdfJsEngine } from '@/core/pdf/PdfJsEngine';

import type { Pixels } from './diff';

/** Rasterise one page of an engine's document and read its pixels back. */
export async function renderPixels(
  engine: PdfJsEngine,
  pageNumber: number,
  scale: number,
  signal?: AbortSignal,
): Promise<Pixels> {
  const canvas = document.createElement('canvas');
  await engine.renderPage(pageNumber, { scale, canvas, signal });
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('2D canvas is not available');
  const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
  return { width: image.width, height: image.height, data: image.data };
}

/** Rescale a render to `width` x `height`, so two pages of different sizes can still be compared. */
export function fitPixels(p: Pixels, width: number, height: number): Pixels {
  if (p.width === width && p.height === height) return p;
  const src = document.createElement('canvas');
  src.width = p.width;
  src.height = p.height;
  src
    .getContext('2d')!
    .putImageData(new ImageData(new Uint8ClampedArray(p.data), p.width, p.height), 0, 0);
  const dst = document.createElement('canvas');
  dst.width = width;
  dst.height = height;
  const ctx = dst.getContext('2d')!;
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(src, 0, 0, width, height);
  const image = ctx.getImageData(0, 0, width, height);
  return { width, height, data: image.data };
}
