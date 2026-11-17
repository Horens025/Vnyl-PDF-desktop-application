import { useEffect, useRef, useState } from 'react';

import { announce } from '@/a11y/announcer';
import { Icon } from '@/components/common';

import { MarkupColorControls } from './MarkupControls';
import { keepSelection } from './keepSelection';
import { hitMarkup } from './hitTest';
import { useAnnotationStore } from './store';
import {
  ANNOTATION_LABELS,
  DEFAULT_HIGHLIGHT_OPACITY,
  MARKUP_SWATCHES,
  markupColor,
  parseRgba,
  type Annotation,
  type TextMarkupType,
} from './types';

interface Anchor {
  id: string;
  top: number;
  left: number;
}

const GAP = 8;
const EDGE = 8;

/** Where on screen a markup's first rect is, from its page element. */
function screenRect(pageEl: HTMLElement, a: Annotation): DOMRect | null {
  const r = a.rects[0];
  if (!r) return null;
  const p = pageEl.getBoundingClientRect();
  return new DOMRect(
    p.left + r.x * p.width,
    p.top + r.y * p.height,
    r.width * p.width,
    r.height * p.height,
  );
}

const sameRgb = (a: number[], b: number[]) => a[0] === b[0] && a[1] === b[1] && a[2] === b[2];

/**
 * Click an existing highlight/underline/strikethrough/squiggly on the page to
 * recolor or delete it. Hit-testing runs on the stored rects rather than on a
 * DOM layer, so the text layer keeps receiving selection gestures untouched.
 */
export function MarkupEditPopup() {
  const [anchor, setAnchor] = useState<Anchor | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const annotation = useAnnotationStore((s) => s.annotations.find((a) => a.id === anchor?.id));
  const recolor = useAnnotationStore((s) => s.recolor);
  const remove = useAnnotationStore((s) => s.remove);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (!target || target.closest('.vnyl-markup-popup, .vnyl-markup-edit')) return;
      // Controls and links on the page keep their own behaviour.
      if (target.closest('button, a, input, select, textarea, .vnyl-note-editor')) return;
      const pageEl = target.closest<HTMLElement>('.vnyl-page');
      if (!pageEl) {
        setAnchor(null);
        return;
      }
      // A drag-selection ends in a click too; leave that to the selection popup.
      const sel = window.getSelection();
      if (sel && !sel.isCollapsed) return;

      const rect = pageEl.getBoundingClientRect();
      const hit = hitMarkup(
        useAnnotationStore.getState().annotations,
        Number(pageEl.dataset.pageNumber),
        (e.clientX - rect.left) / rect.width,
        (e.clientY - rect.top) / rect.height,
      );
      if (!hit) {
        setAnchor(null);
        return;
      }
      const box = screenRect(pageEl, hit);
      if (!box) return;
      const width = ref.current?.offsetWidth ?? 220;
      const height = ref.current?.offsetHeight ?? 84;
      const left = Math.min(
        Math.max(EDGE, box.left + box.width / 2 - width / 2),
        window.innerWidth - width - EDGE,
      );
      const above = box.top - height - GAP;
      setAnchor({
        id: hit.id,
        left,
        top: above >= EDGE ? above : Math.min(box.bottom + GAP, window.innerHeight - height - EDGE),
      });
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAnchor(null);
    };
    const close = () => setAnchor(null);

    document.addEventListener('click', onClick);
    document.addEventListener('keydown', onKey);
    window.addEventListener('resize', close);
    document.addEventListener('scroll', close, true);
    return () => {
      document.removeEventListener('click', onClick);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('resize', close);
      document.removeEventListener('scroll', close, true);
    };
  }, []);

  // The annotation can disappear underneath the popup (undo, page ops, delete).
  if (!anchor || !annotation) return null;

  const type = annotation.type as TextMarkupType;
  const parsed = parseRgba(annotation.color);
  const alpha = parsed?.alpha ?? DEFAULT_HIGHLIGHT_OPACITY;
  const swatch = parsed ? MARKUP_SWATCHES.findIndex((s) => sameRgb(s.rgb, parsed.rgb)) : -1;

  return (
    <div
      ref={ref}
      className="vnyl-markup-popup vnyl-markup-edit"
      role="toolbar"
      aria-label={`Edit ${ANNOTATION_LABELS[type].toLowerCase()}`}
      style={{ top: anchor.top, left: anchor.left }}
      onMouseDown={keepSelection}
    >
      <div className="vnyl-markup-popup__row vnyl-markup-popup__row--colors">
        <MarkupColorControls
          swatch={swatch}
          onSwatch={(i) => recolor(annotation.id, markupColor(type, MARKUP_SWATCHES[i].rgb, alpha))}
          opacity={type === 'highlight' ? alpha : undefined}
          onOpacity={
            type === 'highlight'
              ? (o) =>
                  recolor(
                    annotation.id,
                    markupColor(type, parsed?.rgb ?? MARKUP_SWATCHES[0].rgb, o),
                  )
              : undefined
          }
        />
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label={`Delete ${ANNOTATION_LABELS[type].toLowerCase()}`}
          title="Delete"
          onClick={() => {
            remove(annotation.id);
            announce(`${ANNOTATION_LABELS[type]} deleted`);
            setAnchor(null);
          }}
        >
          <Icon name="trash" size={18} />
        </button>
      </div>
    </div>
  );
}
