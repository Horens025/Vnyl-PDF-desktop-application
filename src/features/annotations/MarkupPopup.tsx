import { useEffect, useRef, useState } from 'react';

import { Icon, type IconName } from '@/components/common';

import { addMarkupFromSelection, addNoteFromSelection, currentMarkupColor } from './commands';
import { MarkupColorControls } from './MarkupControls';
import { keepSelection } from './keepSelection';
import { useMarkupUi } from './markupUi';
import { ANNOTATION_LABELS, TEXT_MARKUP_TYPES, type TextMarkupType } from './types';

const ICONS: Record<TextMarkupType, IconName> = {
  highlight: 'highlighter',
  underline: 'underline',
  strikeout: 'strikethrough',
  squiggly: 'squiggly',
};

interface Position {
  top: number;
  left: number;
}

const GAP = 10;
const EDGE = 8;

/** The selection's page, if the current selection sits inside a rendered page. */
function selectionBox(): DOMRect | null {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  const node = range.startContainer;
  const el = node instanceof HTMLElement ? node : node.parentElement;
  if (!el?.closest('.vnyl-page')) return null;
  const box = range.getBoundingClientRect();
  return box.width > 0 && box.height > 0 ? box : null;
}

/**
 * A floating glass toolbar that appears above selected text (Xodo-style quick
 * markup): highlight, underline, strikethrough, squiggly, comment, plus the
 * color and highlight opacity. It is a mouse/touch convenience; every action
 * has a command and menu entry for keyboard and screen-reader users.
 */
export function MarkupPopup() {
  const [pos, setPos] = useState<Position | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const swatch = useMarkupUi((s) => s.swatch);
  const opacity = useMarkupUi((s) => s.opacity);
  const setSwatch = useMarkupUi((s) => s.setSwatch);
  const setOpacity = useMarkupUi((s) => s.setOpacity);

  useEffect(() => {
    const place = () => {
      const tool = useMarkupUi.getState().tool;
      if (tool && selectionBox()) {
        // A tool is armed (persistent markup toolbar): mark up straight away.
        addMarkupFromSelection(tool, currentMarkupColor(tool));
        setPos(null);
        return;
      }
      const box = selectionBox();
      if (!box) {
        setPos(null);
        return;
      }
      const width = ref.current?.offsetWidth ?? 300;
      const height = ref.current?.offsetHeight ?? 88;
      const left = Math.min(
        Math.max(EDGE, box.left + box.width / 2 - width / 2),
        window.innerWidth - width - EDGE,
      );
      // Above the selection; below it when there is no room at the top.
      const above = box.top - height - GAP;
      const top =
        above >= EDGE ? above : Math.min(box.bottom + GAP, window.innerHeight - height - EDGE);
      setPos({ top, left });
    };

    // Show once the pointer/key interaction that made the selection ends, so
    // the bar does not chase the selection edge while it is being dragged.
    const settle = () => requestAnimationFrame(place);
    const onSelectionChange = () => {
      if (!selectionBox()) setPos(null);
    };
    const hide = () => setPos(null);

    document.addEventListener('mouseup', settle);
    document.addEventListener('keyup', settle);
    document.addEventListener('selectionchange', onSelectionChange);
    window.addEventListener('resize', hide);
    // Scrolling moves the page out from under a fixed-position bar.
    document.addEventListener('scroll', hide, true);
    return () => {
      document.removeEventListener('mouseup', settle);
      document.removeEventListener('keyup', settle);
      document.removeEventListener('selectionchange', onSelectionChange);
      window.removeEventListener('resize', hide);
      document.removeEventListener('scroll', hide, true);
    };
  }, []);

  if (!pos) return null;

  const apply = (type: TextMarkupType) => {
    addMarkupFromSelection(type, currentMarkupColor(type));
    setPos(null);
  };

  return (
    <div
      ref={ref}
      className="vnyl-markup-popup"
      role="toolbar"
      aria-label="Text markup"
      style={{ top: pos.top, left: pos.left }}
      onMouseDown={keepSelection}
    >
      <div className="vnyl-markup-popup__row">
        {TEXT_MARKUP_TYPES.map((type) => (
          <button
            key={type}
            type="button"
            className="vnyl-icon-button"
            aria-label={ANNOTATION_LABELS[type]}
            title={ANNOTATION_LABELS[type]}
            onClick={() => apply(type)}
          >
            <Icon name={ICONS[type]} size={18} />
          </button>
        ))}
        <span className="vnyl-markup-popup__divider" aria-hidden="true" />
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Add comment"
          title="Add comment"
          onClick={() => {
            addNoteFromSelection();
            setPos(null);
          }}
        >
          <Icon name="comment" size={18} />
        </button>
      </div>
      <div className="vnyl-markup-popup__row vnyl-markup-popup__row--colors">
        <MarkupColorControls
          swatch={swatch}
          onSwatch={setSwatch}
          opacity={opacity}
          onOpacity={setOpacity}
        />
      </div>
    </div>
  );
}
