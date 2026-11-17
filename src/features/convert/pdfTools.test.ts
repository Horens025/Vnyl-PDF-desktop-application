import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';

import { extractPages, planSplit, splitFileName, splitPdf } from './pdfTools';

async function samplePdf(pages: number): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  for (let i = 1; i <= pages; i++) doc.addPage([100 + i, 200]); // width encodes the page number
  return doc.save();
}

const widths = async (bytes: Uint8Array) =>
  (await PDFDocument.load(bytes)).getPages().map((p) => Math.round(p.getWidth()));

describe('planSplit', () => {
  it('one file per page', () => {
    expect(planSplit('each', 3, 1, '')).toEqual({
      groups: [
        { from: 1, to: 1 },
        { from: 2, to: 2 },
        { from: 3, to: 3 },
      ],
    });
  });

  it('chunks of N, with a shorter last file', () => {
    expect(planSplit('every', 7, 3, '')).toEqual({
      groups: [
        { from: 1, to: 3 },
        { from: 4, to: 6 },
        { from: 7, to: 7 },
      ],
    });
    expect(planSplit('every', 7, 0, '')).toHaveProperty('error');
    expect(planSplit('every', 7, 2.5, '')).toHaveProperty('error');
  });

  it('each comma-separated range becomes a file, with open ends', () => {
    expect(planSplit('ranges', 10, 1, '1-3, 4-6, 7-')).toEqual({
      groups: [
        { from: 1, to: 3 },
        { from: 4, to: 6 },
        { from: 7, to: 10 },
      ],
    });
    expect(planSplit('ranges', 10, 1, '')).toHaveProperty('error');
    expect(planSplit('ranges', 10, 1, '1-3, 12')).toHaveProperty('error');
    expect(planSplit('each', 0, 1, '')).toHaveProperty('error');
  });
});

describe('split file names', () => {
  it('name the pages held, padded so they sort', () => {
    expect(splitFileName('doc', { from: 4, to: 4 }, 120)).toBe('doc-page-004.pdf');
    expect(splitFileName('doc', { from: 4, to: 12 }, 120)).toBe('doc-pages-004-012.pdf');
    expect(splitFileName('doc', { from: 1, to: 2 }, 9)).toBe('doc-pages-1-2.pdf');
  });
});

describe('extractPages', () => {
  it('keeps the chosen pages in the order given', async () => {
    const out = await extractPages(await samplePdf(5), [4, 2, 5]);
    expect(await widths(out)).toEqual([104, 102, 105]);
  });

  it('rejects empty and out-of-range selections', async () => {
    const pdf = await samplePdf(3);
    await expect(extractPages(pdf, [])).rejects.toThrow(/at least one/);
    await expect(extractPages(pdf, [4])).rejects.toThrow(/between 1 and 3/);
  });

  it('explains garbage input plainly', async () => {
    await expect(extractPages(new Uint8Array([1, 2, 3]), [1])).rejects.toThrow(/could not be read/);
  });
});

describe('splitPdf', () => {
  it('writes each group as its own valid PDF', async () => {
    const files = await splitPdf(
      await samplePdf(5),
      [
        { from: 1, to: 2 },
        { from: 3, to: 3 },
        { from: 4, to: 5 },
      ],
      'doc',
      () => {},
    );
    expect(files.map((f) => f.name)).toEqual([
      'doc-pages-1-2.pdf',
      'doc-page-3.pdf',
      'doc-pages-4-5.pdf',
    ]);
    const w = await Promise.all(
      files.map(async (f) => widths(new Uint8Array(await f.blob.arrayBuffer()))),
    );
    expect(w).toEqual([[101, 102], [103], [104, 105]]);
  });

  it('stops when cancelled', async () => {
    const c = new AbortController();
    c.abort();
    await expect(
      splitPdf(await samplePdf(2), [{ from: 1, to: 1 }], 'd', () => {}, c.signal),
    ).rejects.toThrow(/cancelled/);
  });
});
