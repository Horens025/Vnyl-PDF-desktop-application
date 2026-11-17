import { useEffect } from 'react';

import { announce } from '@/a11y/announcer';
import { Icon, type IconName } from '@/components/common';

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

/**
 * The persistent markup toolbar (Xodo-style): pick a tool and it stays armed,
 * so every passage you then select is marked up at once, without the popup.
 * Toggle it from Annotate > Markup toolbar; Escape disarms the tool.
 */
export function MarkupToolbar() {
  const open = useMarkupUi((s) => s.toolbarOpen);
  const tool = useMarkupUi((s) => s.tool);
  const swatch = useMarkupUi((s) => s.swatch);
  const opacity = useMarkupUi((s) => s.opacity);
  const setTool = useMarkupUi((s) => s.setTool);
  const setSwatch = useMarkupUi((s) => s.setSwatch);
  const setOpacity = useMarkupUi((s) => s.setOpacity);
  const toggle = useMarkupUi((s) => s.toggleToolbar);

  useEffect(() => {
    if (!tool) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setTool(null);
        announce('Markup tool off');
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [tool, setTool]);

  if (!open) return null;

  return (
    <div
      className="vnyl-markup-popup vnyl-markup-toolbar"
      role="toolbar"
      aria-label="Markup toolbar"
      onMouseDown={keepSelection}
    >
      <div className="vnyl-markup-popup__row">
        {TEXT_MARKUP_TYPES.map((type) => (
          <button
            key={type}
            type="button"
            className={`vnyl-icon-button${tool === type ? ' is-active' : ''}`}
            aria-pressed={tool === type}
            aria-label={ANNOTATION_LABELS[type]}
            title={`${ANNOTATION_LABELS[type]} (stays on until you press Esc)`}
            onClick={() => {
              const next = tool === type ? null : type;
              setTool(next);
              announce(
                next
                  ? `${ANNOTATION_LABELS[type]} tool on. Select text to mark it up`
                  : 'Markup tool off',
              );
            }}
          >
            <Icon name={ICONS[type]} size={18} />
          </button>
        ))}
        <span className="vnyl-markup-popup__divider" aria-hidden="true" />
        <MarkupColorControls
          swatch={swatch}
          onSwatch={setSwatch}
          opacity={opacity}
          onOpacity={setOpacity}
        />
        <span className="vnyl-markup-popup__divider" aria-hidden="true" />
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Close markup toolbar"
          title="Close"
          onClick={() => {
            toggle();
            announce('Markup toolbar closed');
          }}
        >
          <Icon name="x" size={18} />
        </button>
      </div>
    </div>
  );
}
