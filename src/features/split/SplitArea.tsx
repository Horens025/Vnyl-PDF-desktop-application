import { useCallback } from 'react';

import { MAX_RATIO, MIN_RATIO, useSplitStore } from './store';
import { SplitPane } from './SplitPane';

/** The draggable, keyboard-adjustable bar between the main view and the second pane. */
function Divider() {
  const orientation = useSplitStore((s) => s.orientation);
  const ratio = useSplitStore((s) => s.ratio);
  const setRatio = useSplitStore((s) => s.setRatio);

  const onPointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      const area = e.currentTarget.parentElement;
      if (!area) return;
      e.preventDefault();
      e.currentTarget.setPointerCapture(e.pointerId);
      const move = (ev: PointerEvent) => {
        const r = area.getBoundingClientRect();
        setRatio(
          orientation === 'columns'
            ? (r.right - ev.clientX) / r.width
            : (r.bottom - ev.clientY) / r.height,
        );
      };
      const up = () => {
        window.removeEventListener('pointermove', move);
        window.removeEventListener('pointerup', up);
      };
      window.addEventListener('pointermove', move);
      window.addEventListener('pointerup', up);
    },
    [orientation, setRatio],
  );

  const onKeyDown = (e: React.KeyboardEvent) => {
    // Arrow toward the second pane to give it less room, away to give it more.
    const grow = orientation === 'columns' ? 'ArrowLeft' : 'ArrowUp';
    const shrink = orientation === 'columns' ? 'ArrowRight' : 'ArrowDown';
    if (e.key === grow) setRatio(ratio + 0.03);
    else if (e.key === shrink) setRatio(ratio - 0.03);
    else if (e.key === 'Home') setRatio(MIN_RATIO);
    else if (e.key === 'End') setRatio(MAX_RATIO);
    else if (e.key === 'Enter') setRatio(0.5);
    else return;
    e.preventDefault();
  };

  return (
    <div
      className="vnyl-split-divider"
      role="separator"
      // WAI-ARIA "window splitter": a focusable separator is what makes it keyboard-resizable.
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      aria-orientation={orientation === 'columns' ? 'vertical' : 'horizontal'}
      aria-label="Resize second document"
      aria-valuemin={Math.round(MIN_RATIO * 100)}
      aria-valuemax={Math.round(MAX_RATIO * 100)}
      aria-valuenow={Math.round(ratio * 100)}
      onPointerDown={onPointerDown}
      onKeyDown={onKeyDown}
    />
  );
}

/** Sets the work-area layout CSS variables, and mounts the divider and pane when split view is open. */
export function SplitArea({ children }: { children: React.ReactNode }) {
  const open = useSplitStore((s) => s.open);
  const source = useSplitStore((s) => s.source);
  const orientation = useSplitStore((s) => s.orientation);
  const ratio = useSplitStore((s) => s.ratio);

  return (
    <div
      className={`vnyl-workarea vnyl-workarea--${orientation}${open && source ? ' vnyl-workarea--split-open' : ''}`}
      style={{ '--split-ratio': ratio } as React.CSSProperties}
    >
      {children}
      {open && (
        <>
          <Divider />
          <SplitPane />
        </>
      )}
    </div>
  );
}
