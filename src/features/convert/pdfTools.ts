import {
  PDFArray,
  PDFDocument,
  PDFName,
  PDFNumber,
  PDFRawStream,
  type PDFDict,
  type PDFObject,
} from 'pdf-lib';

import { ConversionCancelled, type OutputFile, type Progress } from './engines';
import { uniqueName } from './naming';
import { parsePageRange } from './pageRange';

const PDF_MIME = 'application/pdf';

/** pdf-lib's message for a password-protected file is not one a user can act on. */
function friendly(error: unknown): Error {
  const text = error instanceof Error ? error.message : String(error);
  if (/encrypt/i.test(text)) {
    return new Error('This PDF is password protected, so it cannot be changed here.');
  }
  return new Error('This PDF could not be read for this tool.', { cause: error });
}

async function loadPdf(bytes: Uint8Array): Promise<PDFDocument> {
  try {
    return await PDFDocument.load(bytes, { updateMetadata: false });
  } catch (error) {
    throw friendly(error);
  }
}

/** A new PDF holding just `pages` (1-based, in the order given) of `bytes`. */
export async function extractPages(
  bytes: Uint8Array,
  pages: readonly number[],
): Promise<Uint8Array> {
  const src = await loadPdf(bytes);
  const total = src.getPageCount();
  if (pages.length === 0) throw new Error('Choose at least one page.');
  if (pages.some((p) => p < 1 || p > total))
    throw new Error(`Pages must be between 1 and ${total}.`);
  const out = await PDFDocument.create();
  const copied = await out.copyPages(
    src,
    pages.map((p) => p - 1),
  );
  for (const page of copied) out.addPage(page);
  const title = src.getTitle();
  if (title) out.setTitle(title);
  out.setProducer('Vnyl');
  out.setCreator('Vnyl');
  return out.save();
}

export type SplitMode = 'each' | 'every' | 'ranges';

export interface SplitGroup {
  from: number;
  to: number;
}

export type SplitPlan = { groups: SplitGroup[] } | { error: string };

/**
 * Turn a split request into the page groups that become separate files.
 * `each`: one file per page. `every`: chunks of `n` pages. `ranges`: every
 * comma-separated range such as "1-3, 4-6, 7-" becomes one file.
 */
export function planSplit(mode: SplitMode, total: number, n: number, ranges: string): SplitPlan {
  if (total < 1) return { error: 'This document has no pages.' };
  if (mode === 'each') {
    return { groups: Array.from({ length: total }, (_, i) => ({ from: i + 1, to: i + 1 })) };
  }
  if (mode === 'every') {
    if (!Number.isInteger(n) || n < 1)
      return { error: 'Enter a whole number of pages, 1 or more.' };
    const groups: SplitGroup[] = [];
    for (let from = 1; from <= total; from += n)
      groups.push({ from, to: Math.min(total, from + n - 1) });
    return { groups };
  }
  const parts = ranges
    .split(',')
    .map((p) => p.trim())
    .filter(Boolean);
  if (parts.length === 0) return { error: 'Enter at least one range, such as 1-3, 4-6.' };
  const groups: SplitGroup[] = [];
  for (const part of parts) {
    const r = parsePageRange(part, total);
    if ('error' in r) return r;
    groups.push({ from: Math.min(...r.pages), to: Math.max(...r.pages) });
  }
  return { groups };
}

const pad = (n: number, total: number) => String(n).padStart(String(total).length, '0');

/** Name a split file after the pages it holds. */
export function splitFileName(base: string, g: SplitGroup, total: number): string {
  return g.from === g.to
    ? `${base}-page-${pad(g.from, total)}.pdf`
    : `${base}-pages-${pad(g.from, total)}-${pad(g.to, total)}.pdf`;
}

export async function splitPdf(
  bytes: Uint8Array,
  groups: readonly SplitGroup[],
  baseName: string,
  onProgress: (p: Progress) => void,
  signal?: AbortSignal,
): Promise<OutputFile[]> {
  const src = await loadPdf(bytes);
  const total = src.getPageCount();
  const taken = new Set<string>();
  const files: OutputFile[] = [];
  for (let i = 0; i < groups.length; i++) {
    if (signal?.aborted) throw new ConversionCancelled();
    onProgress({ done: i, total: groups.length });
    const g = groups[i];
    const out = await PDFDocument.create();
    const indices = Array.from({ length: g.to - g.from + 1 }, (_, k) => g.from - 1 + k);
    for (const page of await out.copyPages(src, indices)) out.addPage(page);
    out.setProducer('Vnyl');
    out.setCreator('Vnyl');
    const data = await out.save();
    files.push({
      name: uniqueName(splitFileName(baseName, g, total), taken),
      blob: new Blob([new Uint8Array(data)], { type: PDF_MIME }),
    });
    // Let the progress bar paint between files of a long document.
    await new Promise((r) => setTimeout(r, 0));
  }
  onProgress({ done: groups.length, total: groups.length });
  return files;
}

export type CompressPreset = 'screen' | 'balanced' | 'high';

/** Longest image side in pixels, and JPEG quality, for each preset. */
export const COMPRESS_PRESETS: Record<
  CompressPreset,
  { maxSide: number; quality: number; label: string; hint: string }
> = {
  screen: { maxSide: 1000, quality: 0.6, label: 'Smallest', hint: 'Fine for reading on a screen' },
  balanced: { maxSide: 1600, quality: 0.72, label: 'Balanced', hint: 'Good for sharing and email' },
  high: { maxSide: 2400, quality: 0.85, label: 'High quality', hint: 'Keeps detail for printing' },
};

const name = (n: string) => PDFName.of(n);

function isName(obj: PDFObject | undefined, value: string): boolean {
  return obj === name(value);
}

/** Whether `/Filter` is exactly DCTDecode (a JPEG), alone or as a one-item array. */
function isPlainJpeg(filter: PDFObject | undefined): boolean {
  if (isName(filter, 'DCTDecode')) return true;
  return filter instanceof PDFArray && filter.size() === 1 && isName(filter.get(0), 'DCTDecode');
}

/** DeviceRGB, DeviceGray, or an ICC profile with 1 or 3 components. CMYK and indexed colour are left alone. */
function isSupportedColorSpace(dict: PDFDict): boolean {
  const cs = dict.lookup(name('ColorSpace'));
  if (isName(cs, 'DeviceRGB') || isName(cs, 'DeviceGray')) return true;
  if (cs instanceof PDFArray && cs.size() === 2 && isName(cs.get(0), 'ICCBased')) {
    const profile = cs.lookup(1);
    const n =
      profile && 'dict' in profile
        ? (profile as { dict: PDFDict }).dict.lookup(name('N'))
        : undefined;
    return n instanceof PDFNumber && (n.asNumber() === 3 || n.asNumber() === 1);
  }
  return false;
}

export interface CompressResult {
  bytes: Uint8Array;
  before: number;
  after: number;
  /** How many embedded photos were made smaller. */
  recompressed: number;
}

/**
 * Make a PDF smaller by re-encoding its embedded JPEG photos at a lower size
 * and quality. Text, fonts and vector art are untouched, so the document stays
 * searchable and sharp. A photo is only replaced if that really saves space,
 * and the whole result is discarded if the file did not get smaller.
 */
export async function compressPdf(
  bytes: Uint8Array,
  preset: CompressPreset,
  onProgress: (p: Progress) => void,
  signal?: AbortSignal,
): Promise<CompressResult> {
  const { maxSide, quality } = COMPRESS_PRESETS[preset];
  const doc = await loadPdf(bytes);
  const context = doc.context;
  const candidates = context
    .enumerateIndirectObjects()
    .filter(
      ([, obj]) => obj instanceof PDFRawStream && isName(obj.dict.get(name('Subtype')), 'Image'),
    );

  let recompressed = 0;
  for (let i = 0; i < candidates.length; i++) {
    if (signal?.aborted) throw new ConversionCancelled();
    onProgress({ done: i, total: candidates.length });
    const [ref, obj] = candidates[i];
    const stream = obj as PDFRawStream;
    const dict = stream.dict;
    if (!isPlainJpeg(dict.get(name('Filter')))) continue;
    // Masks and inverted decode arrays change what the pixels mean; leave those images as they are.
    if (dict.has(name('ImageMask')) || dict.has(name('Mask')) || dict.has(name('Decode'))) continue;
    if (!isSupportedColorSpace(dict)) continue;

    const original = stream.getContents();
    try {
      // 'none': a PDF viewer ignores EXIF orientation inside an embedded JPEG, so we must too.
      const bitmap = await createImageBitmap(
        new Blob([new Uint8Array(original)], { type: 'image/jpeg' }),
        {
          imageOrientation: 'none',
        },
      );
      const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
      const width = Math.max(1, Math.round(bitmap.width * scale));
      const height = Math.max(1, Math.round(bitmap.height * scale));
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        bitmap.close();
        continue;
      }
      ctx.fillStyle = '#fff';
      ctx.fillRect(0, 0, width, height);
      ctx.drawImage(bitmap, 0, 0, width, height);
      bitmap.close();
      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', quality),
      );
      canvas.width = canvas.height = 0;
      if (!blob) continue;
      const next = new Uint8Array(await blob.arrayBuffer());
      if (next.length >= original.length * 0.92) continue; // not worth a second generation of loss

      const fresh = context.obj({}) as PDFDict;
      for (const [key, value] of dict.entries()) {
        const k = key.asString();
        if (
          [
            '/Filter',
            '/DecodeParms',
            '/ColorSpace',
            '/Width',
            '/Height',
            '/BitsPerComponent',
            '/Length',
          ].includes(k)
        )
          continue;
        fresh.set(key, value);
      }
      fresh.set(name('Type'), name('XObject'));
      fresh.set(name('Subtype'), name('Image'));
      fresh.set(name('Width'), PDFNumber.of(width));
      fresh.set(name('Height'), PDFNumber.of(height));
      fresh.set(name('ColorSpace'), name('DeviceRGB'));
      fresh.set(name('BitsPerComponent'), PDFNumber.of(8));
      fresh.set(name('Filter'), name('DCTDecode'));
      fresh.set(name('Length'), PDFNumber.of(next.length));
      context.assign(ref, PDFRawStream.of(fresh, next));
      recompressed++;
    } catch {
      // An image the browser cannot decode keeps its original bytes.
    }
  }
  onProgress({ done: candidates.length, total: candidates.length });

  doc.setProducer('Vnyl');
  const out = await doc.save({ useObjectStreams: true });
  if (out.length >= bytes.length) {
    return { bytes, before: bytes.length, after: bytes.length, recompressed: 0 };
  }
  return { bytes: out, before: bytes.length, after: out.length, recompressed };
}
