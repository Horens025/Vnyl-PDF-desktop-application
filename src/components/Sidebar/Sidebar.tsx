import { useEffect, useState } from 'react';

import { AnnotationsPanel } from '@/features/annotations';
import { SignaturesPanel } from '@/features/signatures';
import { useContributionStore } from '@/plugins';
import { useViewerStore } from '@/state/viewerStore';

import { Outline } from './Outline';
import { PluginPanel } from './PluginPanel';
import { Thumbnails } from './Thumbnails';
import { CORE_SIDEBAR_PANELS } from './panelCatalog';

const SIDEBAR_WIDTH_KEY = 'vnyl.sidebar.width';
const SIDEBAR_MIN_WIDTH = 210;
const SIDEBAR_MAX_WIDTH = 480;

function clampSidebarWidth(width: number): number {
  return Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, width));
}

function readSidebarWidth(): number {
  try {
    const width = Number(window.localStorage.getItem(SIDEBAR_WIDTH_KEY));
    return Number.isFinite(width) && width > 0 ? clampSidebarWidth(width) : 264;
  } catch {
    return 264;
  }
}

export function Sidebar() {
  const tab = useViewerStore((s) => s.sidebarTab);
  const panels = useContributionStore((s) => s.sidebarPanels);
  const [width, setWidth] = useState(readSidebarWidth);

  useEffect(() => {
    try {
      window.localStorage.setItem(SIDEBAR_WIDTH_KEY, String(Math.round(width)));
    } catch {
      // The sidebar remains resizable for this session when storage is blocked.
    }
  }, [width]);

  const resizeWithKeyboard = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowRight') setWidth((current) => clampSidebarWidth(current + 16));
    else if (event.key === 'ArrowLeft') setWidth((current) => clampSidebarWidth(current - 16));
    else if (event.key === 'Home') setWidth(SIDEBAR_MIN_WIDTH);
    else if (event.key === 'End') setWidth(SIDEBAR_MAX_WIDTH);
    else return;
    event.preventDefault();
  };

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    const move = (pointer: PointerEvent) => setWidth(clampSidebarWidth(pointer.clientX));
    const stop = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', stop);
      window.removeEventListener('pointercancel', stop);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', stop, { once: true });
    window.addEventListener('pointercancel', stop, { once: true });
  };

  const activePanel = panels.find((p) => p.id === tab);
  const activeTitle =
    CORE_SIDEBAR_PANELS.find((panel) => panel.id === tab)?.title ?? activePanel?.title ?? 'Sidebar';

  return (
    <aside
      className="vnyl-sidebar"
      aria-label="Document tools"
      style={{ '--vnyl-sidebar-width': `${width}px` } as React.CSSProperties}
    >
      <div className="vnyl-sidebar__body" role="region" aria-label={activeTitle}>
        {tab === 'thumbnails' && <Thumbnails />}
        {tab === 'outline' && <Outline />}
        {tab === 'annotations' && <AnnotationsPanel />}
        {tab === 'signatures' && <SignaturesPanel />}
        {activePanel && <PluginPanel panel={activePanel} />}
      </div>
      <div
        className="vnyl-sidebar__resize"
        role="separator"
        // WAI-ARIA window splitter: a focusable separator provides a keyboard resize path.
        // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
        tabIndex={0}
        aria-orientation="vertical"
        aria-label="Resize sidebar"
        aria-valuemin={SIDEBAR_MIN_WIDTH}
        aria-valuemax={SIDEBAR_MAX_WIDTH}
        aria-valuenow={Math.round(width)}
        aria-valuetext={`${Math.round(width)} pixels wide`}
        title="Drag to resize sidebar; use left and right arrow keys"
        onPointerDown={startResize}
        onKeyDown={resizeWithKeyboard}
      />
    </aside>
  );
}
