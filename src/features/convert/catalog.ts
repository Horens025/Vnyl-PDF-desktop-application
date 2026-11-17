import type { IconName } from '@/components/common';

export type ToolPanel =
  | 'pdf-to-images'
  | 'pdf-to-text'
  | 'images-to-pdf'
  | 'split-pdf'
  | 'extract-pages'
  | 'compress-pdf';
export interface ToolDef {
  id: string;
  title: string;
  description: string;
  icon: IconName;
  /** Opens one of the hub's own panels. */
  panel?: ToolPanel;
  /** Or runs an existing command (the hub closes first). */
  command?: string;
  /** Does it need an open document? Used to explain a disabled card. */
  needsDocument?: boolean;
}

export interface ToolCategory {
  id: string;
  title: string;
  tools: ToolDef[];
}

/** The tool gallery, grouped like Xodo's: convert from, convert to, organize, edit, view. */
export const CATALOG: ToolCategory[] = [
  {
    id: 'from-pdf',
    title: 'Convert from PDF',
    tools: [
      {
        id: 'pdf-to-images',
        title: 'PDF to images',
        description: 'Save pages as PNG, JPG or WebP at the resolution you choose.',
        icon: 'image',
        panel: 'pdf-to-images',
        needsDocument: true,
      },
      {
        id: 'pdf-to-text',
        title: 'PDF to text',
        description: 'Extract the text into a .txt or Markdown file.',
        icon: 'type',
        panel: 'pdf-to-text',
        needsDocument: true,
      },
    ],
  },
  {
    id: 'to-pdf',
    title: 'Convert to PDF',
    tools: [
      {
        id: 'ocr',
        title: 'OCR (make searchable)',
        description: 'Recognise the text in a scanned PDF so you can search and copy it.',
        icon: 'scan',
        command: 'ocr.recognizeDocument',
        needsDocument: true,
      },
      {
        id: 'images-to-pdf',
        title: 'Images to PDF',
        description: 'JPG, PNG, WebP, BMP and GIF into one PDF, in the order you choose.',
        icon: 'image',
        panel: 'images-to-pdf',
      },
    ],
  },
  {
    id: 'organize',
    title: 'Organize',
    tools: [
      {
        id: 'merge',
        title: 'Merge PDFs',
        description: 'Combine several PDFs into one.',
        icon: 'combine',
        command: 'file.combine',
      },
      {
        id: 'organize',
        title: 'Rotate, delete & rearrange',
        description: 'Drag pages into order, rotate them or remove them.',
        icon: 'list',
        command: 'pageops.organize',
        needsDocument: true,
      },
      {
        id: 'split-pdf',
        title: 'Split PDF',
        description: 'By page ranges, every N pages, or into single pages.',
        icon: 'split-columns',
        panel: 'split-pdf',
        needsDocument: true,
      },
      {
        id: 'extract-pages',
        title: 'Extract pages',
        description: 'Pull selected pages into a new PDF.',
        icon: 'hash',
        panel: 'extract-pages',
        needsDocument: true,
      },
    ],
  },
  {
    id: 'edit',
    title: 'Edit & secure',
    tools: [
      {
        id: 'annotate',
        title: 'Markup toolbar',
        description: 'Highlight, underline, strike through and squiggle.',
        icon: 'highlighter',
        command: 'annotate.toolbar',
        needsDocument: true,
      },
      {
        id: 'sign',
        title: 'Add signature',
        description: 'Draw or place your signature.',
        icon: 'signature',
        command: 'sign.addSignature',
        needsDocument: true,
      },
      {
        id: 'digital-sign',
        title: 'Digitally sign',
        description: 'A certificate-based signature that detects changes.',
        icon: 'shield',
        command: 'sign.digitallySign',
        needsDocument: true,
      },
      {
        id: 'compress-pdf',
        title: 'Compress',
        description: 'Make the file smaller.',
        icon: 'download',
        panel: 'compress-pdf',
        needsDocument: true,
      },
    ],
  },
  {
    id: 'view',
    title: 'View & share',
    tools: [
      {
        id: 'compare',
        title: 'Compare documents',
        description: 'See what changed between two versions.',
        icon: 'split-columns',
        command: 'tools.compare',
      },
      {
        id: 'split-view',
        title: 'Split view',
        description: 'Read two documents, or two places, side by side.',
        icon: 'split-columns',
        command: 'view.toggleSplit',
        needsDocument: true,
      },
      {
        id: 'print',
        title: 'Print',
        description: 'Send the document to a printer.',
        icon: 'print',
        command: 'file.print',
        needsDocument: true,
      },
    ],
  },
];

export function findTool(id: string): ToolDef | undefined {
  for (const c of CATALOG) {
    const t = c.tools.find((x) => x.id === id);
    if (t) return t;
  }
  return undefined;
}

/** Case-insensitive match on title and description. */
export function filterCatalog(query: string): ToolCategory[] {
  const q = query.trim().toLowerCase();
  if (!q) return CATALOG;
  return CATALOG.map((c) => ({
    ...c,
    tools: c.tools.filter((t) => `${t.title} ${t.description}`.toLowerCase().includes(q)),
  })).filter((c) => c.tools.length > 0);
}
