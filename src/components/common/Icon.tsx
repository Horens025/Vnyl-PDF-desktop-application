import type { ReactNode, SVGProps } from 'react';

/**
 * A small, self-contained icon set (no runtime icon dependency). Icons are
 * stroked with `currentColor`, so they inherit text color and adapt to theme.
 */
const ICONS = {
  'folder-open': (
    <>
      <path d="M3 7a2 2 0 0 1 2-2h3l2 2h5a2 2 0 0 1 2 2v1" />
      <path d="M3 9h16.5a1 1 0 0 1 .96 1.27l-1.6 6A2 2 0 0 1 17 18H5a2 2 0 0 1-2-2Z" />
    </>
  ),
  'zoom-in': (
    <>
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
      <line x1="11" y1="8" x2="11" y2="14" />
      <line x1="8" y1="11" x2="14" y2="11" />
    </>
  ),
  'zoom-out': (
    <>
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
      <line x1="8" y1="11" x2="14" y2="11" />
    </>
  ),
  'fit-width': (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M8 12h8" />
      <path d="M10 9l-3 3 3 3" />
      <path d="M14 9l3 3-3 3" />
    </>
  ),
  'fit-page': (
    <>
      <rect x="5" y="3" width="14" height="18" rx="2" />
      <path d="M12 8v8" />
      <path d="M9 10l3-3 3 3" />
      <path d="M9 14l3 3 3-3" />
    </>
  ),
  sidebar: (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <line x1="9" y1="4" x2="9" y2="20" />
    </>
  ),
  panels: (
    <>
      <rect x="3" y="4" width="8" height="7" rx="1.5" />
      <rect x="13" y="4" width="8" height="7" rx="1.5" />
      <rect x="3" y="13" width="8" height="7" rx="1.5" />
      <rect x="13" y="13" width="8" height="7" rx="1.5" />
    </>
  ),
  sparkles: (
    <>
      <path d="m12 3 1.7 5.3L19 10l-5.3 1.7L12 17l-1.7-5.3L5 10l5.3-1.7L12 3Z" />
      <path d="m19 15 .8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z" />
    </>
  ),
  window: (
    <>
      <rect x="4" y="4" width="15" height="15" rx="2" />
      <path d="M8 4V2.8a1 1 0 0 1 1-1h11.2a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H19" />
      <path d="M4 8h15" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: <path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8Z" />,
  contrast: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3a9 9 0 0 1 0 18Z" fill="currentColor" stroke="none" />
    </>
  ),
  'chevron-left': <path d="M15 18l-6-6 6-6" />,
  'chevron-right': <path d="M9 18l6-6-6-6" />,
  'chevron-down': <path d="M6 9l6 6 6-6" />,
  'chevron-up': <path d="M6 15l6-6 6 6" />,
  undo: (
    <>
      <path d="M9 14L4 9l5-5" />
      <path d="M4 9h11a5 5 0 0 1 0 10h-4" />
    </>
  ),
  // Three quarters of a circle with an arrowhead on the open end, so the two
  // read as mirrored turns of the same shape.
  'rotate-left': (
    <>
      <path d="M4 12a8 8 0 1 0 2.3-5.6" />
      <path d="M4 4v5h5" />
    </>
  ),
  'rotate-right': (
    <>
      <path d="M20 12a8 8 0 1 1-2.3-5.6" />
      <path d="M20 4v5h-5" />
    </>
  ),
  x: (
    <>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </>
  ),
  trash: (
    <>
      <path d="M3 6h18" />
      <path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2" />
      <path d="M6 6l1 14a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-14" />
    </>
  ),
  hash: (
    <>
      <line x1="4" y1="9" x2="20" y2="9" />
      <line x1="4" y1="15" x2="20" y2="15" />
      <line x1="10" y1="3" x2="8" y2="21" />
      <line x1="16" y1="3" x2="14" y2="21" />
    </>
  ),
  highlighter: (
    <>
      <path d="M15 5l4 4-8 8H7l-2-2Z" />
      <path d="M5 19h6" />
    </>
  ),
  underline: (
    <>
      <path d="M7 4v7a5 5 0 0 0 10 0V4" />
      <line x1="5" y1="21" x2="19" y2="21" />
    </>
  ),
  strikethrough: (
    <>
      <path d="M16 6.5A4 3 0 0 0 12 5c-2.5 0-4 1.2-4 3 0 4 8 2.5 8 7 0 1.8-1.7 3-4 3a4.6 3.2 0 0 1-4.5-2" />
      <line x1="4" y1="12" x2="20" y2="12" />
    </>
  ),
  squiggly: (
    <>
      <path d="M7 4v6a5 5 0 0 0 10 0V4" />
      <path d="M3 20c1.5-2 3-2 4.5 0s3 2 4.5 0 3-2 4.5 0 3 2 4.5 0" />
    </>
  ),
  image: (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="8.5" cy="9.5" r="1.5" />
      <path d="M21 16l-5-5L5 20" />
    </>
  ),
  type: (
    <>
      <path d="M4 7V5h16v2" />
      <path d="M12 5v14" />
      <path d="M9 19h6" />
    </>
  ),
  pencil: (
    <>
      <path d="M4 20l1-4L16 5l3 3L8 19l-4 1Z" />
      <path d="M14 7l3 3" />
    </>
  ),
  scan: (
    <>
      <path d="M4 8V6a2 2 0 0 1 2-2h2" />
      <path d="M16 4h2a2 2 0 0 1 2 2v2" />
      <path d="M20 16v2a2 2 0 0 1-2 2h-2" />
      <path d="M8 20H6a2 2 0 0 1-2-2v-2" />
      <path d="M7 12h10" />
    </>
  ),
  hand: (
    <>
      <path d="M8 11V5.5a1.5 1.5 0 0 1 3 0V11" />
      <path d="M11 11V4.5a1.5 1.5 0 0 1 3 0V11" />
      <path d="M14 11.5V6a1.5 1.5 0 0 1 3 0v7" />
      <path d="M8 11v-.5a1.5 1.5 0 0 0-3 0v3.5a7 7 0 0 0 7 7 6 6 0 0 0 6-6V9" />
    </>
  ),
  'auto-scroll': (
    <>
      <rect x="5" y="3" width="14" height="18" rx="2" />
      <path d="M9 9l3 3 3-3" />
      <path d="M9 14l3 3 3-3" />
    </>
  ),
  cursor: <path d="M4 3l7 17 2.4-6.6L20 11 4 3Z" />,
  copy: (
    <>
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </>
  ),
  check: <path d="M20 6L9 17l-5-5" />,
  // The cross-mark glyph (features/editing's MarkGlyph): visually an X, like
  // `x` above, but named separately so a call site reads as "cross mark"
  // rather than "close/delete".
  cross: (
    <>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5" />
      <path d="M12 8h.01" />
    </>
  ),
  list: (
    <>
      <line x1="8" y1="6" x2="20" y2="6" />
      <line x1="8" y1="12" x2="20" y2="12" />
      <line x1="8" y1="18" x2="20" y2="18" />
      <circle cx="4" cy="6" r="0.6" fill="currentColor" />
      <circle cx="4" cy="12" r="0.6" fill="currentColor" />
      <circle cx="4" cy="18" r="0.6" fill="currentColor" />
    </>
  ),
  note: (
    <>
      <path d="M4 4h16v10l-6 6H4Z" />
      <path d="M14 20v-6h6" />
    </>
  ),
  comment: (
    <>
      <path d="M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-4 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z" />
      <path d="M8 10h8M8 13h5" />
    </>
  ),
  download: (
    <>
      <path d="M12 3v12" />
      <path d="M7 10l5 5 5-5" />
      <path d="M5 21h14" />
    </>
  ),
  save: (
    <>
      <path d="M4 4h12l4 4v12H4Z" />
      <path d="M8 4v5h7" />
      <path d="M7 20v-6h10v6" />
    </>
  ),
  print: (
    <>
      <path d="M7 9V3h10v6" />
      <path d="M7 18H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-2" />
      <path d="M7 14h10v7H7Z" />
    </>
  ),
  signature: (
    <>
      <path d="M3 17c2.5 0 2.5-9 5-9s2 9 4.5 9 3-4 5.5-4" />
      <path d="M3 21h18" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3l7 3v5c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6l7-3Z" />
      <path d="M9 12l2 2 4-4" />
    </>
  ),
  more: (
    <>
      <circle cx="12" cy="5.5" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="12" cy="18.5" r="1.4" fill="currentColor" stroke="none" />
    </>
  ),
  menu: (
    <>
      <line x1="4" y1="6" x2="20" y2="6" />
      <line x1="4" y1="12" x2="20" y2="12" />
      <line x1="4" y1="18" x2="20" y2="18" />
    </>
  ),
  // Corner brackets around empty space: a frame ready to be moved or
  // resized, distinct from `image` (a filled picture frame) so the "Edit
  // images" toolbar button reads as a different action from "Add image".
  crop: (
    <>
      <path d="M4 8V5a1 1 0 0 1 1-1h3" />
      <path d="M20 8V5a1 1 0 0 0-1-1h-3" />
      <path d="M4 16v3a1 1 0 0 0 1 1h3" />
      <path d="M20 16v3a1 1 0 0 1-1 1h-3" />
    </>
  ),
  // Two overlapping pages, evoking several documents folding into one -- the
  // File > Combine PDFs… menu entry.
  combine: (
    <>
      <rect x="3" y="4" width="12" height="15" rx="2" />
      <rect x="9" y="7" width="12" height="15" rx="2" />
    </>
  ),
  file: (
    <>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
    </>
  ),
  'split-columns': (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <line x1="12" y1="4" x2="12" y2="20" />
    </>
  ),
  'split-rows': (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <line x1="3" y1="12" x2="21" y2="12" />
    </>
  ),
  book: (
    <>
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21Z" />
      <path d="M4 5.5v15M8 7h8M8 10h8" />
    </>
  ),
  maximize: (
    <>
      <path d="M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3M21 16v3a2 2 0 0 1-2 2h-3M3 16v3a2 2 0 0 0 2 2h3" />
    </>
  ),
  link: (
    <>
      <path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7" />
      <path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" />
    </>
  ),
  plus: (
    <>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </>
  ),
} satisfies Record<string, ReactNode>;

export type IconName = keyof typeof ICONS;

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName;
  size?: number;
}

export function Icon({ name, size = 20, className, ...props }: IconProps) {
  return (
    <svg
      className={['vnyl-icon', className].filter(Boolean).join(' ')}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {ICONS[name]}
    </svg>
  );
}
