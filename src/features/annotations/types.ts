/** Text-markup kinds that decorate selected text (they share rects/text/color). */
export type TextMarkupType = 'highlight' | 'underline' | 'strikeout' | 'squiggly';
export type AnnotationType = TextMarkupType | 'note';

export const TEXT_MARKUP_TYPES: readonly TextMarkupType[] = [
  'highlight',
  'underline',
  'strikeout',
  'squiggly',
];

export const isTextMarkup = (type: AnnotationType): type is TextMarkupType =>
  (TEXT_MARKUP_TYPES as readonly string[]).includes(type);

/** Human-readable names, shared by the panel, popup and announcements. */
export const ANNOTATION_LABELS: Record<AnnotationType, string> = {
  highlight: 'Highlight',
  underline: 'Underline',
  strikeout: 'Strikethrough',
  squiggly: 'Squiggly underline',
  note: 'Comment',
};

/** A rectangle expressed as fractions (0..1) of the page, so it survives zoom. */
export interface NormalizedRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Annotation {
  id: string;
  type: AnnotationType;
  /** 1-based page. */
  pageNumber: number;
  /** CSS color (may be translucent). */
  color: string;
  /** One rect per line of a highlight; empty for a point note. */
  rects: NormalizedRect[];
  /**
   * Anchor point as page fractions (0..1), for a sticky note. This is where the
   * pin sits and what the AI export reports so a note can be located on the page.
   */
  anchor?: { x: number; y: number };
  /**
   * For a highlight: the highlighted text. For a note: the page text nearest the
   * anchor, captured at placement so an AI reading the doc back can tell what the
   * note refers to.
   */
  text?: string;
  /** The reviewer's comment (the body of a sticky note). */
  note?: string;
  createdAt: number;
}

export interface HighlightColor {
  name: string;
  value: string;
}

export const HIGHLIGHT_COLORS: HighlightColor[] = [
  { name: 'Yellow', value: 'rgba(255, 214, 10, 0.45)' },
  { name: 'Green', value: 'rgba(52, 199, 89, 0.40)' },
  { name: 'Blue', value: 'rgba(0, 122, 255, 0.35)' },
  { name: 'Pink', value: 'rgba(255, 45, 85, 0.32)' },
];

export const DEFAULT_HIGHLIGHT_COLOR = HIGHLIGHT_COLORS[0].value;

/** Base colors (RGB) for the markup color picker. */
export interface MarkupSwatch {
  name: string;
  rgb: [number, number, number];
}

export const MARKUP_SWATCHES: MarkupSwatch[] = [
  { name: 'Yellow', rgb: [255, 214, 10] },
  { name: 'Green', rgb: [52, 199, 89] },
  { name: 'Blue', rgb: [10, 132, 255] },
  { name: 'Pink', rgb: [255, 45, 85] },
  { name: 'Orange', rgb: [255, 149, 0] },
  { name: 'Purple', rgb: [175, 82, 222] },
];

export const DEFAULT_HIGHLIGHT_OPACITY = 0.45;

/**
 * Compose the stored CSS color for a markup. Only a highlight is translucent;
 * underline, strikethrough and squiggly are drawn as opaque lines.
 */
export function markupColor(
  type: TextMarkupType,
  rgb: [number, number, number],
  opacity: number = DEFAULT_HIGHLIGHT_OPACITY,
): string {
  const alpha = type === 'highlight' ? Math.min(1, Math.max(0.1, opacity)) : 1;
  return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${alpha})`;
}

/** Parse `rgb()`/`rgba()` back into parts, e.g. to recolor while keeping alpha. */
export function parseRgba(color: string): { rgb: [number, number, number]; alpha: number } | null {
  const m = color.match(/rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+))?/i);
  if (!m) return null;
  return {
    rgb: [Number(m[1]), Number(m[2]), Number(m[3])],
    alpha: m[4] === undefined ? 1 : Number(m[4]),
  };
}
