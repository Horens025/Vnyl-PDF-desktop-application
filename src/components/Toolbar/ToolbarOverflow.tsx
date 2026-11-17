import { useEffect, useRef, useState } from 'react';

import { Icon, IconButton } from '@/components/common';

import type { OverflowTool } from './toolbarTools';
import { ToolbarAction } from './ToolbarAction';

interface ToolbarOverflowProps {
  items: OverflowTool[];
/** How many leading items to show inline; the rest collapse into the menu. */
  visibleCount: number;
  /** Entries that always live in the menu, after the collapsed items — the
      controls CSS folds out of the bar on narrow viewports. Never inline. */
  trailingItems?: OverflowTool[];
/** Keep a named menu visible instead of inlining its rows. */
  alwaysMenu?: boolean;
  triggerLabel?: string;
  triggerIcon?: import('@/components/common').IconName;
  compactTrigger?: boolean;
}

/**
 * Renders the first `visibleCount` toolbar tools inline and collapses the rest
 * into a labeled dropdown, so a crowded toolbar never clips its controls off
 * the edge on a narrow window. `visibleCount` is computed by the Toolbar from a
 * width measurement; this component only renders the split.
 */
export function ToolbarOverflow({
  items,
  visibleCount,
  trailingItems,
  alwaysMenu = false,
  triggerLabel = 'More tools',
  triggerIcon = 'chevron-down',
  compactTrigger = false,
}: ToolbarOverflowProps) {
  const visible = alwaysMenu ? [] : items.slice(0, visibleCount);
  const overflow = [...items.slice(alwaysMenu ? 0 : visibleCount), ...(trailingItems ?? [])];
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('pointerdown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('pointerdown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  // If the window widened enough that nothing overflows anymore, drop the menu.
  useEffect(() => {
    if (overflow.length === 0) setOpen(false);
  }, [overflow.length]);

  return (
    <>
      {visible.map((tool) => (
        tool.displayLabel ? (
          <ToolbarAction
            key={tool.id}
            icon={tool.icon}
            label={tool.label}
            displayLabel={tool.displayLabel}
            active={tool.active}
            disabled={tool.disabled}
            onClick={tool.onClick}
          />
        ) : (
          <IconButton
            key={tool.id}
            icon={tool.icon}
            label={tool.label}
            active={tool.active}
            disabled={tool.disabled}
            // Comment/Highlight read the live text selection on click; a plain
            // button mousedown would collapse it first.
            onMouseDown={tool.preserveSelection ? (e) => e.preventDefault() : undefined}
            onClick={tool.onClick}
          />
        )
      ))}
      {overflow.length > 0 && (
        <div className={`vnyl-dropdown${alwaysMenu ? ' vnyl-toolbar__actions-menu' : ''}`} ref={ref}>
          <IconButton
            icon={triggerIcon}
            label={triggerLabel}
            className={[
              alwaysMenu ? 'vnyl-toolbar__actions-trigger' : 'vnyl-toolbar__overflow-trigger',
              compactTrigger ? 'vnyl-toolbar__compact-trigger' : '',
            ].filter(Boolean).join(' ')}
            active={open}
            aria-haspopup="menu"
            aria-expanded={open}
            onClick={() => setOpen((o) => !o)}
          >
            {!compactTrigger && <span>{triggerLabel}</span>}
          </IconButton>
          {open && (
            <div className="vnyl-dropdown__menu" role="menu" aria-label={triggerLabel}>
              {overflow.map((tool) => (
                <button
                  key={tool.id}
                  type="button"
                  role={tool.selection === 'radio' ? 'menuitemradio' : 'menuitem'}
                  aria-checked={tool.selection === 'radio' ? Boolean(tool.active) : undefined}
                  className={`vnyl-dropdown__item${tool.active ? ' is-active' : ''}`}
                  disabled={tool.disabled}
                  onMouseDown={tool.preserveSelection ? (e) => e.preventDefault() : undefined}
                  onClick={() => {
                    setOpen(false);
                    tool.onClick();
                  }}
                >
                  <Icon name={tool.icon} size={16} />
                  <span className="vnyl-dropdown__label">{tool.menuLabel}</span>
                  {tool.active && <Icon name="check" size={16} />}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </>
  );
}
