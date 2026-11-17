import type { PdfJsEngine } from '@/core/pdf/PdfJsEngine';

import { applyTemplate, uniqueName } from './naming';

export interface OutputFile {
  name: string;
  blob: Blob;
}

export interface Progress {
  done: number;
  total: number;
}

export class ConversionCancelled extends Error {
  constructor() {
    super('Conversion cancelled');
    this.name = 'ConversionCancelled';
  }
}

export type ImageFormat = 'png' | 'jpeg' | 'webp';

export const IMAGE_FORMATS: Record<ImageFormat, { mime: string; ext: string; label: string }> = {
  png: { mime: 'image/png', ext: 'png', label: 'PNG' },
  jpeg: { mime: 'image/jpeg', ext: 'jpg', label: 'JPG' },
  webp: { mime: 'image/webp', ext: 'webp', label: 'WebP' },
};

/** The engine's canvases are capped at this many pixels on the longest side. */
export const MAX_IMAGE_SIDE = 4096;

const throwIfAborted = (signal?: AbortSignal) => {
  if (signal?.aborted) throw new ConversionCancelled();
};

const toBlob = (canvas: HTMLCanvasElement, mime: string, quality: number) =>
  new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error(`This browser cannot encode ${mime}`))),
      mime,
      quality,
    ),
  );

export interface ImageJob {
  pages: number[];
  totalPages: number;
  format: ImageFormat;
  dpi: number;
  /** 0.1 to 1; only used for JPG and WebP. */
  quality: number;
  template: string;
  baseName: string;
}

export interface ImageResult {
  files: OutputFile[];
  /** Pages that came out smaller than asked because they hit the canvas size limit. */
  limited: number;
}

/** Rasterise the chosen pages at `dpi` and encode each as an image. */
export async function pdfToImages(
  engine: PdfJsEngine,
  job: ImageJob,
  onProgress: (p: Progress) => void,
  signal?: AbortSignal,
): Promise<ImageResult> {
  const { mime, ext } = IMAGE_FORMATS[job.format];
  // renderPage draws its backing store at max(2, devicePixelRatio) times the
  // scale it is given, so divide that out to land on the DPI that was asked for.
  const density = Math.max(2, window.devicePixelRatio || 1);
  const scale = job.dpi / 72 / density;
  const taken = new Set<string>();
  const files: OutputFile[] = [];
  let limited = 0;

  for (let i = 0; i < job.pages.length; i++) {
    throwIfAborted(signal);
    const page = job.pages[i];
    onProgress({ done: i, total: job.pages.length });

    const render = document.createElement('canvas');
    await engine.renderPage(page, { scale, canvas: render, signal });
    throwIfAborted(signal);

    const size = await engine.getPageDimensions(page, 1);
    if (render.width < Math.round((size.width * job.dpi) / 72) * 0.98) limited++;

    // Flatten onto white: a PDF page has no background of its own, and JPEG has no alpha.
    const out = document.createElement('canvas');
    out.width = render.width;
    out.height = render.height;
    const ctx = out.getContext('2d');
    if (!ctx) throw new Error('2D canvas is not available');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, out.width, out.height);
    ctx.drawImage(render, 0, 0);
    render.width = render.height = 0;

    const blob = await toBlob(out, mime, job.quality);
    out.width = out.height = 0;
    const stem = applyTemplate(job.template, { name: job.baseName, page, total: job.totalPages });
    files.push({ name: uniqueName(`${stem}.${ext}`, taken), blob });
  }
  onProgress({ done: job.pages.length, total: job.pages.length });
  return { files, limited };
}

export type TextFormat = 'txt' | 'md';

export interface TextJob {
  pages: number[];
  format: TextFormat;
  baseName: string;
}

/** Extract the text of the chosen pages into one .txt or .md file. */
export async function pdfToText(
  engine: PdfJsEngine,
  job: TextJob,
  onProgress: (p: Progress) => void,
  signal?: AbortSignal,
): Promise<{ file: OutputFile; text: string; emptyPages: number }> {
  const parts: string[] = [];
  let emptyPages = 0;
  for (let i = 0; i < job.pages.length; i++) {
    throwIfAborted(signal);
    onProgress({ done: i, total: job.pages.length });
    const page = job.pages[i];
    const text = (await engine.getPageText(page)).trim();
    if (text === '') emptyPages++;
    parts.push(
      job.format === 'md'
        ? `## Page ${page}\n\n${text || '*(no text on this page)*'}\n`
        : `--- Page ${page} ---\n\n${text || '(no text on this page)'}\n`,
    );
  }
  onProgress({ done: job.pages.length, total: job.pages.length });
  const text = parts.join('\n');
  return {
    text,
    emptyPages,
    file: {
      name: `${job.baseName}.${job.format}`,
      blob: new Blob([text], { type: job.format === 'md' ? 'text/markdown' : 'text/plain' }),
    },
  };
}

/** Save a blob through a temporary link. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}
