import { PDFDocument } from 'pdf-lib';

import { ConversionCancelled, type Progress } from './engines';

export type PageSize = 'fit' | 'a4' | 'letter';
export type Orientation = 'auto' | 'portrait' | 'landscape';

export interface PageOptions {
  size: PageSize;
  orientation: Orientation;
  /** Margin on every side, in PDF points (72 per inch). */
  margin: number;
}

/** [short side, long side] in points. */
export const PAGE_SIZES: Record<Exclude<PageSize, 'fit'>, [number, number]> = {
  a4: [595.28, 841.89],
  letter: [612, 792],
};

/** "Fit to image" pages treat a pixel as 1/96 inch, as browsers do, capped at this length. */
const PX_TO_PT = 72 / 96;
const MAX_FIT_SIDE = 1440;

export interface PageLayout {
  pageWidth: number;
  pageHeight: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Where an image of `imgW` x `imgH` pixels goes on its page (PDF coordinates: origin bottom-left). */
export function layoutPage(imgW: number, imgH: number, opts: PageOptions): PageLayout {
  if (imgW <= 0 || imgH <= 0) throw new Error('The image has no size');

  if (opts.size === 'fit') {
    let scale = PX_TO_PT;
    const longest = Math.max(imgW, imgH) * scale;
    if (longest > MAX_FIT_SIDE) scale *= MAX_FIT_SIDE / longest;
    const width = imgW * scale;
    const height = imgH * scale;
    const m = Math.max(0, opts.margin);
    return { pageWidth: width + 2 * m, pageHeight: height + 2 * m, x: m, y: m, width, height };
  }

  const [short, long] = PAGE_SIZES[opts.size];
  const landscape = opts.orientation === 'auto' ? imgW > imgH : opts.orientation === 'landscape';
  const pageWidth = landscape ? long : short;
  const pageHeight = landscape ? short : long;
  // A margin that would leave nothing to draw on is ignored rather than honoured.
  const m = opts.margin * 2 >= Math.min(pageWidth, pageHeight) ? 0 : Math.max(0, opts.margin);
  const scale = Math.min((pageWidth - 2 * m) / imgW, (pageHeight - 2 * m) / imgH);
  const width = imgW * scale;
  const height = imgH * scale;
  return {
    pageWidth,
    pageHeight,
    x: (pageWidth - width) / 2,
    y: (pageHeight - height) / 2,
    width,
    height,
  };
}

/**
 * The EXIF orientation (1-8) of a JPEG, or 1 if there is none. Phone photos are
 * stored sideways with this tag saying how to turn them; a PDF embeds the raw
 * JPEG and ignores the tag, so a tagged photo has to be re-drawn upright first.
 */
export function jpegOrientation(bytes: Uint8Array): number {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return 1;
  let pos = 2;
  while (pos + 4 <= bytes.length && bytes[pos] === 0xff) {
    const marker = bytes[pos + 1];
    if (marker === 0xda || marker === 0xd9) break; // image data begins
    const segLen = (bytes[pos + 2] << 8) | bytes[pos + 3];
    if (marker === 0xe1 && segLen >= 16) {
      const isExif =
        bytes[pos + 4] === 0x45 &&
        bytes[pos + 5] === 0x78 &&
        bytes[pos + 6] === 0x69 &&
        bytes[pos + 7] === 0x66;
      if (isExif) {
        const tiff = pos + 10;
        const little = bytes[tiff] === 0x49;
        const u16 = (o: number) =>
          little ? bytes[o] | (bytes[o + 1] << 8) : (bytes[o] << 8) | bytes[o + 1];
        const u32 = (o: number) =>
          little
            ? (bytes[o] | (bytes[o + 1] << 8) | (bytes[o + 2] << 16) | (bytes[o + 3] << 24)) >>> 0
            : ((bytes[o] << 24) | (bytes[o + 1] << 16) | (bytes[o + 2] << 8) | bytes[o + 3]) >>> 0;
        const ifd = tiff + u32(tiff + 4);
        if (ifd + 2 > bytes.length) return 1;
        const count = u16(ifd);
        for (let i = 0; i < count; i++) {
          const entry = ifd + 2 + i * 12;
          if (entry + 12 > bytes.length) return 1;
          if (u16(entry) === 0x0112) {
            const value = u16(entry + 8);
            return value >= 1 && value <= 8 ? value : 1;
          }
        }
        return 1;
      }
    }
    pos += 2 + segLen;
  }
  return 1;
}

export type SniffedType = 'jpeg' | 'png' | 'other';

/** Identify an image by its first bytes: a file's extension and MIME type are only hints. */
export function sniffImage(bytes: Uint8Array): SniffedType {
  if (bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff)
    return 'jpeg';
  if (
    bytes.length > 7 &&
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47
  ) {
    return 'png';
  }
  return 'other';
}

const canvasBlob = (canvas: HTMLCanvasElement, mime: string, quality?: number) =>
  new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error('Could not encode the image'))),
      mime,
      quality,
    ),
  );

/** Decode any image the browser understands, upright, and re-encode it as JPEG (opaque) or PNG (has alpha). */
async function redraw(
  file: File,
  keepAlpha: boolean,
): Promise<{ bytes: Uint8Array; type: 'jpeg' | 'png' }> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  try {
    const canvas = document.createElement('canvas');
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('2D canvas is not available');
    if (!keepAlpha) {
      ctx.fillStyle = '#fff';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
    }
    ctx.drawImage(bitmap, 0, 0);
    const blob = keepAlpha
      ? await canvasBlob(canvas, 'image/png')
      : await canvasBlob(canvas, 'image/jpeg', 0.92);
    canvas.width = canvas.height = 0;
    return { bytes: new Uint8Array(await blob.arrayBuffer()), type: keepAlpha ? 'png' : 'jpeg' };
  } finally {
    bitmap.close();
  }
}

/**
 * Build a PDF with one image per page, in the order given. JPEG and PNG files
 * are embedded as they are (no recompression, so no quality loss). Anything
 * else, and any JPEG that needs turning upright, is drawn to a canvas first.
 */
export async function imagesToPdf(
  files: readonly File[],
  opts: PageOptions,
  onProgress: (p: Progress) => void,
  signal?: AbortSignal,
): Promise<Uint8Array> {
  if (files.length === 0) throw new Error('Add at least one image.');
  const doc = await PDFDocument.create();
  doc.setCreator('Vnyl');
  doc.setProducer('Vnyl');

  for (let i = 0; i < files.length; i++) {
    if (signal?.aborted) throw new ConversionCancelled();
    onProgress({ done: i, total: files.length });
    const file = files[i];
    try {
      const raw = new Uint8Array(await file.arrayBuffer());
      const type = sniffImage(raw);
      let image;
      if (type === 'jpeg' && jpegOrientation(raw) === 1) {
        image = await doc.embedJpg(raw);
      } else if (type === 'png') {
        image = await doc.embedPng(raw);
      } else {
        // WebP, BMP, GIF (first frame), or a sideways JPEG. Only formats that can
        // carry transparency keep an alpha channel.
        const redrawn = await redraw(file, type === 'other');
        image =
          redrawn.type === 'png'
            ? await doc.embedPng(redrawn.bytes)
            : await doc.embedJpg(redrawn.bytes);
      }
      const layout = layoutPage(image.width, image.height, opts);
      const page = doc.addPage([layout.pageWidth, layout.pageHeight]);
      page.drawImage(image, {
        x: layout.x,
        y: layout.y,
        width: layout.width,
        height: layout.height,
      });
    } catch (error) {
      if (error instanceof ConversionCancelled) throw error;
      throw new Error(`Could not read “${file.name}”. Is it a valid image?`, { cause: error });
    }
  }
  onProgress({ done: files.length, total: files.length });
  return doc.save();
}
