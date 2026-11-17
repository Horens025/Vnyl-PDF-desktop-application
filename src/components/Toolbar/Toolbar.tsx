import { useEffect, useState } from 'react';

import { announce } from '@/a11y/announcer';
import { commandRegistry } from '@/commands';
import { IconButton, pushToast } from '@/components/common';
import { isTauri } from '@/core/document/openDocument';
import { useTextEditStore } from '@/features/textedit';
import { useContributionStore } from '@/plugins';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import {
  DOCUMENT_MUTATION_BUSY_TITLE,
  useDocumentMutationBlocked,
} from '@/state/documentMutationStore';
import { useDocumentStore } from '@/state/documentStore';
import { focusViewer } from '@/state/viewerElement';
import { AUTO_SCROLL_MAX, AUTO_SCROLL_MIN, useViewerStore } from '@/state/viewerStore';
import { COMPACT_VIEWPORT_QUERY, NARROW_VIEWPORT_QUERY } from '@/theme/breakpoints';
import { useThemeStore, type PdfColorMode } from '@/theme/themeStore';

import { AppearanceMenu } from './AppearanceMenu';
import { ToolbarOverflow } from './ToolbarOverflow';
import { ToolbarAction } from './ToolbarAction';
import { EditToolsToolbar } from './EditToolsToolbar';
import type { OverflowTool } from './toolbarTools';
import { CORE_SIDEBAR_PANELS } from '@/components/Sidebar/panelCatalog';

const run = (id: string) => commandRegistry.execute(id);

async function openExtraWindow(): Promise<void> {
  try {
    if (isTauri()) {
      const { WebviewWindow } = await import('@tauri-apps/api/webviewWindow');
      const extra = new WebviewWindow(`extra-${Date.now()}`, {
        url: 'index.html',
        title: 'Vnyl',
        width: 1280,
        height: 840,
        minWidth: 720,
        minHeight: 520,
        center: true,
      });
      void extra.once('tauri://error', (event) => {
        const detail = typeof event.payload === 'string' ? ` ${event.payload}` : '';
        pushToast(`Could not open another window.${detail}`, 'error');
      });
      return;
    }

    const extra = window.open(window.location.href, '_blank');
    if (!extra) pushToast('Allow pop-ups to open another Vnyl window.', 'info');
    else extra.opener = null;
  } catch (error) {
    const detail = error instanceof Error ? ` ${error.message}` : '';
    pushToast(`Could not open another window.${detail}`, 'error');
  }
}

// Cycle order for the mobile "Dark color" menu entry (the desktop pinned tail
// offers the same schemes as a dropdown, which a flat menu row can't host).
const PDF_COLOR_ORDER: PdfColorMode[] = ['light', 'dark', 'sepia', 'green', 'amber'];
const PDF_COLOR_LABELS: Record<PdfColorMode, string> = {
  light: 'Light', dark: 'Dark', sepia: 'Sepia', green: 'Green', amber: 'Amber',
};

// The auto-scroll slider is geometric, not linear: equal slider travel is equal
// *ratio* of speed change, so the slow end (where fine control matters) gets as
// much of the track as the fast end. Position runs 0..1.
const speedToSlider = (speed: number): number =>
  Math.log(speed / AUTO_SCROLL_MIN) / Math.log(AUTO_SCROLL_MAX / AUTO_SCROLL_MIN);
const sliderToSpeed = (pos: number): number =>
  AUTO_SCROLL_MIN * (AUTO_SCROLL_MAX / AUTO_SCROLL_MIN) ** pos;

/** The top application toolbar. */
export function Toolbar({
  readingMode,
  onToggleReadingMode,
}: {
  readingMode: boolean;
  onToggleReadingMode: () => void;
}) {
  const hasDoc = useDocumentStore((s) => s.status === 'ready');
  const docName = useDocumentStore((s) => s.info?.name);
  const textEditActive = useTextEditStore((s) => s.active);
  const sidebarOpen = useViewerStore((s) => s.sidebarOpen);
  const sidebarTab = useViewerStore((s) => s.sidebarTab);
  const searchOpen = useViewerStore((s) => s.searchOpen);
  const handMode = useViewerStore((s) => s.handMode);
  const fitMode = useViewerStore((s) => s.fitMode);
  const setFitMode = useViewerStore((s) => s.setFitMode);
  const editToolbarOpen = useViewerStore((s) => s.editToolbarOpen);
  const setEditToolbarOpen = useViewerStore((s) => s.setEditToolbarOpen);
  const autoScroll = useViewerStore((s) => s.autoScroll);
  const sidebarPanels = useContributionStore((s) => s.sidebarPanels);
  const resolvedTheme = useThemeStore((s) => s.resolvedTheme);
  const pdfColorMode = useThemeStore((s) => s.pdfColorMode);
  const setTheme = useThemeStore((s) => s.setTheme);
  const setPdfColorMode = useThemeStore((s) => s.setPdfColorMode);
  const isNarrow = useMediaQuery(NARROW_VIEWPORT_QUERY);
  const isCompact = useMediaQuery(COMPACT_VIEWPORT_QUERY);
  // Some OTHER feature is mid-flight rewriting the document; Save and Open
  // both have to wait for it (see documentMutationStore.ts, saveDocument.ts,
  // and state/actions.ts). Two owners because they are two different
  // operations: an export reads a snapshot, an open replaces the document.
  const saveBusy = useDocumentMutationBlocked('export', 'content');
  const openBusy = useDocumentMutationBlocked('document', 'pages');

  // Keep the core document tools on the toolbar; the narrow layout can still
  // fold them into a menu so they remain reachable on small screens.
  const docTools: OverflowTool[] = [
    {
      id: 'extra-window',
      icon: 'window',
      label: 'Open an extra Vnyl window',
      displayLabel: 'Extra window',
      menuLabel: 'Extra window',
      onClick: () => void openExtraWindow(),
    },
    {
      id: 'reading-mode',
      icon: 'maximize',
      label: readingMode ? 'Exit distraction-free reading mode' : 'Enter distraction-free reading mode',
      displayLabel: readingMode ? 'Reading' : 'Focus',
      menuLabel: readingMode ? 'Exit reading mode' : 'Distraction-free reading mode',
      active: readingMode,
      disabled: !hasDoc,
      onClick: onToggleReadingMode,
    },
    {
      id: 'edit',
      icon: 'pencil',
      label: 'Edit and annotate PDF',
      displayLabel: 'Edit',
      menuLabel: 'Edit tools',
      active: editToolbarOpen || textEditActive,
      disabled: !hasDoc,
      onClick: () => setEditToolbarOpen(true),
    },
    {
      id: 'find',
      icon: 'search',
      label: 'Find (Ctrl/Cmd + F)',
      displayLabel: 'Search',
      menuLabel: 'Find',
      active: searchOpen,
      disabled: !hasDoc,
      onClick: () => run('search.toggle'),
    },
  ];

  const sidebarPanelTools: OverflowTool[] = [
    ...CORE_SIDEBAR_PANELS.map((panel) => ({
      id: `sidebar-${panel.id}`,
      icon: panel.icon,
      label: `Show ${panel.title.toLowerCase()} in the sidebar`,
      menuLabel:
        panel.id === 'annotations'
          ? 'Annotation list'
          : panel.id === 'signatures'
            ? 'Saved signatures'
            : panel.title,
      active: sidebarTab === panel.id,
      selection: 'radio' as const,
      disabled: panel.id !== 'thumbnails' && !hasDoc,
      onClick: () => useViewerStore.getState().setSidebarTab(panel.id),
    })),
    ...sidebarPanels.map((panel) => ({
      id: `sidebar-${panel.id}`,
      icon: (panel.icon as OverflowTool['icon']) ?? 'note',
      label: `Show ${panel.title} in the sidebar`,
      menuLabel: panel.title,
      active: sidebarTab === panel.id,
      selection: 'radio' as const,
      onClick: () => useViewerStore.getState().setSidebarTab(panel.id),
    })),
  ];

  // Controls that CSS folds out of the bar on narrow viewports (the
  // __fold-narrow / __fold-compact classes and the pinned tail) reappear here,
  // appended to the More menu, so nothing becomes unreachable on a phone.
  const foldedTools: OverflowTool[] = isNarrow
    ? [
        ...(isCompact
          ? ([
              {
                id: 'zoom-out',
                icon: 'zoom-out',
                label: 'Zoom out (Ctrl/Cmd + -)',
                menuLabel: 'Zoom out',
                disabled: !hasDoc,
                onClick: () => run('view.zoomOut'),
              },
              {
                id: 'zoom-in',
                icon: 'zoom-in',
                label: 'Zoom in (Ctrl/Cmd + =)',
                menuLabel: 'Zoom in',
                disabled: !hasDoc,
                onClick: () => run('view.zoomIn'),
              },
            ] satisfies OverflowTool[])
          : []),
        {
          id: 'fit-toggle',
          icon: fitMode === 'width' ? 'fit-page' : 'fit-width',
          label: fitMode === 'width' ? 'Switch to fit page' : 'Switch to fit width',
          menuLabel: fitMode === 'width' ? 'Fit page' : 'Fit width',
          disabled: !hasDoc,
          onClick: () => setFitMode(fitMode === 'width' ? 'page' : 'width'),
        },
        {
          id: 'hand',
          icon: 'hand',
          label: 'Hand tool (pan to scroll)',
          menuLabel: 'Hand tool',
          active: handMode,
          disabled: !hasDoc,
          onClick: () => run('view.toggleHandMode'),
        },
        {
          id: 'auto-scroll',
          icon: 'auto-scroll',
          label: 'Auto-scroll (continuous)',
          menuLabel: 'Auto-scroll',
          active: autoScroll,
          disabled: !hasDoc,
          onClick: () => run('view.toggleAutoScroll'),
        },
        {
          id: 'dark-scheme',
          icon: 'contrast',
          label: `PDF color mode: ${PDF_COLOR_LABELS[pdfColorMode]} (tap to change)`,
          menuLabel: `PDF color: ${PDF_COLOR_LABELS[pdfColorMode]}`,
          onClick: () => {
            const next = PDF_COLOR_ORDER[(PDF_COLOR_ORDER.indexOf(pdfColorMode) + 1) % PDF_COLOR_ORDER.length];
            setPdfColorMode(next);
            // The row's own label only updates behind the now-closed menu, so
            // the announcement is the only feedback a screen reader gets. Same
            // wording as the appearance menu on wider viewports.
            announce(`PDF color mode ${PDF_COLOR_LABELS[next]}`);
          },
        },
        {
          // The pinned AppearanceMenu folds away at this width, so the mode it
          // owns reappears here as a cycling row, matching the dark-scheme row
          // above rather than nesting a second dropdown inside the More menu.
          id: 'theme-mode',
          icon: resolvedTheme === 'dark' ? 'moon' : 'sun',
          label: `App theme: ${resolvedTheme === 'dark' ? 'Dark' : 'Light'} (tap to change)`,
          menuLabel: `Theme: ${resolvedTheme === 'dark' ? 'Dark' : 'Light'}`,
          onClick: () => {
            const next = resolvedTheme === 'dark' ? 'light' : 'dark';
            setTheme(next);
            announce(`App theme ${next}`);
          },
        },
        {
          id: 'print',
          icon: 'print',
          label: 'Print document (Ctrl/Cmd + P)',
          menuLabel: 'Print',
          disabled: !hasDoc || saveBusy,
          onClick: () => run('file.print'),
        },
      ]
    : [];

  if (editToolbarOpen) return <EditToolsToolbar />;

  return (
    <header className="vnyl-toolbar" role="banner">
      <div className="vnyl-toolbar__group vnyl-toolbar__group--left">
        <ToolbarAction
          icon="chevron-left"
          label="Back to the workspace"
          className="vnyl-toolbar__fold-compact"
          disabled={!hasDoc || openBusy}
          onClick={() => run('file.close')}
        />
        <ToolbarOverflow
          items={sidebarPanelTools}
          visibleCount={0}
          alwaysMenu
          compactTrigger
          triggerIcon="panels"
          triggerLabel="Panels"
        />
        <ToolbarAction
          icon="save"
          label="Save"
          disabled={!hasDoc || saveBusy}
          onClick={() => run('file.save')}
        />
        <ToolbarAction
          icon="download"
          label="Save as"
          className="vnyl-toolbar__fold-narrow"
          disabled={!hasDoc || saveBusy}
          onClick={() => run('file.saveAs')}
        />
        <ToolbarAction
          icon="sparkles"
          label="Open Actions: convert, organize, edit, compare and share PDFs"
          className="vnyl-toolbar-action--actions"
          onClick={() => run('tools.hub')}
        />
        <ToolbarAction
          icon="sidebar"
          label="Toggle sidebar (Ctrl/Cmd + B)"
          ariaKeyShortcuts="Control+B Meta+B"
          active={sidebarOpen}
          onClick={() => run('view.toggleSidebar')}
        />
        <span className="vnyl-toolbar__divider" aria-hidden="true" />
        <IconButton
          icon="folder-open"
          label={
            openBusy
              ? `Open document (Ctrl/Cmd + O). ${DOCUMENT_MUTATION_BUSY_TITLE}`
              : 'Open document (Ctrl/Cmd + O)'
          }
          disabled={openBusy}
          onClick={() => run('file.open')}
        />
        {hasDoc && docName && (
          <span className="vnyl-toolbar__title" title={docName}>
            {docName}
          </span>
        )}
      </div>

      <div className="vnyl-toolbar__group vnyl-toolbar__group--center" aria-hidden="true" />

      <div className="vnyl-toolbar__group vnyl-toolbar__group--right">
        <ToolbarOverflow
          items={docTools}
          visibleCount={isNarrow ? 0 : docTools.length}
          trailingItems={foldedTools}
          triggerLabel="More"
        />
        {/* Theme and Print stay visible; document actions live in More. */}
        <div className="vnyl-toolbar__pinned">
          <AppearanceMenu />
          <IconButton
            icon="print"
            label="Print document (Ctrl/Cmd + P)"
            disabled={!hasDoc || saveBusy}
            onClick={() => run('file.print')}
          />
        </div>
      </div>
    </header>
  );
}

/** Page navigation and zoom controls, lifted into a floating pill over the viewer. */
export function PageViewControls() {
  const hasDoc = useDocumentStore((s) => s.status === 'ready');
  const editToolbarOpen = useViewerStore((s) => s.editToolbarOpen);
  const currentPage = useViewerStore((s) => s.currentPage);
  const numPages = useViewerStore((s) => s.numPages);
  const scale = useViewerStore((s) => s.scale);
  const fitMode = useViewerStore((s) => s.fitMode);
  const setFitMode = useViewerStore((s) => s.setFitMode);
  const handMode = useViewerStore((s) => s.handMode);
  const autoScroll = useViewerStore((s) => s.autoScroll);
  const autoScrollSpeed = useViewerStore((s) => s.autoScrollSpeed);
  const setAutoScrollSpeed = useViewerStore((s) => s.setAutoScrollSpeed);
  const goToPage = useViewerStore((s) => s.goToPage);

  if (!hasDoc || numPages === 0 || editToolbarOpen) return null;

  return (
    <div className="vnyl-view-controls-pill" role="toolbar" aria-label="Page navigation and zoom">
      <IconButton
        icon="chevron-left"
        label="Previous page (←)"
        disabled={currentPage <= 1}
        onClick={() => run('nav.prevPage')}
      />
      <PageBox currentPage={currentPage} numPages={numPages} disabled={false} onSubmit={goToPage} />
      <IconButton
        icon="chevron-right"
        label="Next page (→)"
        disabled={currentPage >= numPages}
        onClick={() => run('nav.nextPage')}
      />
      <span className="vnyl-toolbar__divider" aria-hidden="true" />
      <IconButton icon="zoom-out" label="Zoom out (Ctrl/Cmd + -)" onClick={() => run('view.zoomOut')} />
      <span className="vnyl-toolbar__zoom" aria-live="polite">{Math.round(scale * 100)}%</span>
      <IconButton icon="zoom-in" label="Zoom in (Ctrl/Cmd + =)" onClick={() => run('view.zoomIn')} />
      <span className="vnyl-toolbar__divider" aria-hidden="true" />
      <IconButton
        icon={fitMode === 'width' ? 'fit-page' : 'fit-width'}
        label={fitMode === 'width' ? 'Switch to fit page' : 'Switch to fit width'}
        active={fitMode !== 'custom'}
        onClick={() => setFitMode(fitMode === 'width' ? 'page' : 'width')}
      />
      <IconButton icon="hand" label="Hand tool (pan to scroll)" active={handMode} onClick={() => run('view.toggleHandMode')} />
      <IconButton icon="auto-scroll" label="Auto-scroll (continuous)" active={autoScroll} onClick={() => run('view.toggleAutoScroll')} />
      {autoScroll && (
        <span className="vnyl-toolbar__speed-slot">
          <input
            className="vnyl-toolbar__speed"
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={speedToSlider(autoScrollSpeed)}
            aria-label={`Auto-scroll speed, ${autoScrollSpeed} pixels per second`}
            title="Auto-scroll speed (slower ← → faster)"
            onChange={(e) => setAutoScrollSpeed(sliderToSpeed(Number(e.target.value)))}
          />
        </span>
      )}
    </div>
  );
}

interface PageBoxProps {
  currentPage: number;
  numPages: number;
  disabled: boolean;
  onSubmit: (page: number) => void;
}

function PageBox({ currentPage, numPages, disabled, onSubmit }: PageBoxProps) {
  const [value, setValue] = useState(String(currentPage));

  useEffect(() => {
    setValue(String(currentPage));
  }, [currentPage]);

  return (
    <span className="vnyl-pagebox">
      <input
        className="vnyl-pagebox__input"
        type="text"
        inputMode="numeric"
        // Nothing open: show an empty box and a dash, not a nonsensical "1 / 0".
        value={numPages > 0 ? value : ''}
        placeholder={numPages > 0 ? undefined : '–'}
        disabled={disabled}
        aria-label={numPages > 0 ? `Page number, ${currentPage} of ${numPages}` : 'Page number'}
        onChange={(e) => setValue(e.target.value)}
        onFocus={(e) => e.currentTarget.select()}
        onBlur={() => setValue(String(currentPage))}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            const n = parseInt(value, 10);
            if (!Number.isNaN(n)) onSubmit(n);
            // Move focus to the document rather than blurring to <body>, which
            // would leave the scroll keys with no scrollable element to act on.
            focusViewer();
          } else if (e.key === 'Escape') {
            setValue(String(currentPage));
            focusViewer();
          }
        }}
      />
      <span className="vnyl-pagebox__total">/ {numPages > 0 ? numPages : '–'}</span>
    </span>
  );
}
