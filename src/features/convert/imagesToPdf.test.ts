import { describe, expect, it } from 'vitest';

import { jpegOrientation, layoutPage, PAGE_SIZES, sniffImage } from './imagesToPdf';

const none = { orientation: 'auto' as const, margin: 0 };

describe('layoutPage', () => {
  it('fit: the page is the image at 96 dpi, plus margins', () => {
    const l = layoutPage(800, 400, { size: 'fit', ...none });
    expect([l.pageWidth, l.pageHeight]).toEqual([600, 300]);
    expect([l.x, l.y, l.width, l.height]).toEqual([0, 0, 600, 300]);
    const m = layoutPage(800, 400, { size: 'fit', orientation: 'auto', margin: 10 });
    expect([m.pageWidth, m.pageHeight, m.x, m.y]).toEqual([620, 320, 10, 10]);
  });

  it('fit: caps a huge image at 1440 pt on its longest side', () => {
    const l = layoutPage(8000, 4000, { size: 'fit', ...none });
    expect(l.pageWidth).toBeCloseTo(1440);
    expect(l.pageHeight).toBeCloseTo(720);
  });

  it('A4 auto turns the page to match the image and centres it', () => {
    const wide = layoutPage(1600, 800, { size: 'a4', ...none });
    expect([wide.pageWidth, wide.pageHeight]).toEqual([PAGE_SIZES.a4[1], PAGE_SIZES.a4[0]]);
    expect(wide.width).toBeCloseTo(PAGE_SIZES.a4[1]);
    expect(wide.y).toBeCloseTo((PAGE_SIZES.a4[0] - wide.height) / 2);
    const tall = layoutPage(800, 1600, { size: 'a4', ...none });
    expect([tall.pageWidth, tall.pageHeight]).toEqual([PAGE_SIZES.a4[0], PAGE_SIZES.a4[1]]);
  });

  it('honours a forced orientation and keeps the image inside the margins', () => {
    const l = layoutPage(1000, 1000, { size: 'letter', orientation: 'landscape', margin: 36 });
    expect([l.pageWidth, l.pageHeight]).toEqual([792, 612]);
    expect(l.width).toBeCloseTo(612 - 72);
    expect(l.x).toBeGreaterThanOrEqual(36);
    expect(l.x + l.width).toBeLessThanOrEqual(792 - 36 + 1e-9);
  });

  it('ignores a margin that would leave no room, and rejects an empty image', () => {
    const l = layoutPage(100, 100, { size: 'letter', orientation: 'portrait', margin: 400 });
    expect(l.width).toBeCloseTo(612);
    expect(() => layoutPage(0, 10, { size: 'fit', ...none })).toThrow();
  });
});

/** A minimal JPEG header carrying an EXIF orientation tag. */
function jpegWithOrientation(value: number, little: boolean): Uint8Array {
  const u16 = (n: number) => (little ? [n & 255, n >> 8] : [n >> 8, n & 255]);
  const u32 = (n: number) =>
    little ? [n & 255, (n >> 8) & 255, 0, 0] : [0, 0, (n >> 8) & 255, n & 255];
  const tiff = [
    ...(little ? [0x49, 0x49] : [0x4d, 0x4d]),
    ...u16(42),
    ...u32(8),
    ...u16(1),
    ...u16(0x0112),
    ...u16(3),
    ...u32(1),
    ...u16(value),
    0,
    0,
    ...u32(0),
  ];
  const body = [0x45, 0x78, 0x69, 0x66, 0, 0, ...tiff];
  const len = body.length + 2;
  return new Uint8Array([0xff, 0xd8, 0xff, 0xe1, len >> 8, len & 255, ...body, 0xff, 0xda, 0, 2]);
}

describe('jpegOrientation', () => {
  it('reads the tag in both byte orders', () => {
    expect(jpegOrientation(jpegWithOrientation(6, true))).toBe(6);
    expect(jpegOrientation(jpegWithOrientation(8, false))).toBe(8);
    expect(jpegOrientation(jpegWithOrientation(1, true))).toBe(1);
  });

  it('is 1 without EXIF, for junk, and for out-of-range values', () => {
    expect(jpegOrientation(new Uint8Array([0xff, 0xd8, 0xff, 0xda, 0, 2]))).toBe(1);
    expect(jpegOrientation(new Uint8Array([1, 2, 3, 4, 5]))).toBe(1);
    expect(jpegOrientation(new Uint8Array())).toBe(1);
    expect(jpegOrientation(jpegWithOrientation(9, true))).toBe(1);
  });
});

describe('sniffImage', () => {
  it('goes by the bytes, not the name', () => {
    expect(sniffImage(new Uint8Array([0xff, 0xd8, 0xff, 0xe0]))).toBe('jpeg');
    expect(sniffImage(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10]))).toBe('png');
    expect(sniffImage(new Uint8Array([0x52, 0x49, 0x46, 0x46]))).toBe('other');
  });
});
