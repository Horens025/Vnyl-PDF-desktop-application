import { memo, useCallback, useEffect, useRef, useState } from 'react';

import 'pdfjs-dist/web/pdf_viewer.css';

import { Icon } from '@/components/common';
import { pickAndReadDocument } from '@/core/document/openDocument';
import { PdfJsEngine } from '@/core/pdf/PdfJsEngine';
import type { DocumentSource } from '@/core/pdf';
import { AnnotationLayer } from '@/features/annotations';
import { getTabSource, useWorkspaceStore } from '@/features/workspace';
import { useNearViewport } from '@/hooks/useNearViewport';
import { getViewerElement } from '@/state/viewerElement';
import { DARK_SCHEME_TINT, useThemeStore } from '@/theme/themeStore';

import { scrollFraction, scrollTopForFraction, useSplitStore } from './store';

const FILE_OPTION = '__file__';
const MIN_SCALE = 0.25;
const MAX_SCALE = 5;
const PAD = 24;

type Size = { width: number; height: number };

interface SplitPageProps {
  engine: PdfJsEngine;
  pageNumber: number;
  scale: number;
  size: Size | undefined;
  fallback: Size;
  onMeasure(pageNumber: number, size: Size): void;
  /** Draw the main view's markups on this page (only valid when it is the same document). */
  showMarkup: boolean;
  generation: number;
}

/** One read-only page: canvas plus a real text layer, drawn lazily near the viewport. */
const SplitPage = memo(function SplitPage({
  engine,
  pageNumber,
  scale,
  size,
  fallback,
  onMeasure,
  showMarkup,
  generation,
}: SplitPageProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const textRef = useRef<HTMLDivElement>(null);
  const pdfColorMode = useThemeStore((s) => s.pdfColorMode);
  const dark = pdfColorMode === 'dark' || pdfColorMode === 'green' || pdfColorMode === 'amber';
  const tint = pdfColorMode === 'green' ? DARK_SCHEME_TINT.green! : pdfColorMode === 'amber' ? DARK_SCHEME_TINT.amber! : undefined;

  const raster = useNearViewport(wrapperRef, '600px 0px', '.vnyl-split__scroll');
  const near = useNearViewport(wrapperRef, '1600px 0px', '.vnyl-split__scroll');

  useEffect(() => {
    if (!near || size) return;
    let live = true;
    void engine.getPageDimensions(pageNumber, 1).then((d) => {
      if (live) onMeasure(pageNumber, { width: d.width, height: d.height });
    });
    return () => {
      live = false;
    };
  }, [near, size, engine, pageNumber, onMeasure, generation]);

  const dims = size ?? fallback;
  const w = dims.width * scale;
  const h = dims.height * scale;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (!raster || !size) {
      canvas.width = 0;
      canvas.height = 0;
      return;
    }
    const controller = new AbortController();
    engine
      .renderPage(pageNumber, { scale, canvas, signal: controller.signal, invert: dark, tint })
      .catch(() => {
        /* aborted by scrolling away or a zoom change */
      });
    return () => controller.abort();
  }, [raster, size, engine, pageNumber, scale, dark, tint, generation]);

  useEffect(() => {
    const container = textRef.current;
    if (!container) return;
    if (!near || !size) {
      container.replaceChildren();
      return;
    }
    const controller = new AbortController();
    engine
      .renderTextLayer(pageNumber, container, { scale, signal: controller.signal })
      .catch(() => {
        /* aborted */
      });
    return () => controller.abort();
  }, [near, size, engine, pageNumber, scale, generation]);

  return (
    <div
      ref={wrapperRef}
      className={`vnyl-split-page${pdfColorMode === 'sepia' ? ' vnyl-page--sepia' : ''}${dark ? ' vnyl-page--pdf-dark' : ''}`}
      data-split-page={pageNumber}
      role="group"
      aria-label={`Page ${pageNumber}`}
      style={{ width: w, height: h }}
    >
      <canvas ref={canvasRef} className="vnyl-page-canvas" aria-hidden="true" />
      <div ref={textRef} className="textLayer vnyl-text-layer" />
      {showMarkup && near && <AnnotationLayer pageNumber={pageNumber} />}
    </div>
  );
});

/**
 * The second, read-only document pane. It owns its own PdfJsEngine instance, so
 * it can show any open tab (or the same one) beside the main view without
 * touching the single-document state everything else depends on.
 */
export function SplitPane() {
  const source = useSplitStore((s) => s.source);
  const orientation = useSplitStore((s) => s.orientation);
  const sync = useSplitStore((s) => s.sync);
  const setSource = useSplitStore((s) => s.setSource);
  const setOrientation = useSplitStore((s) => s.setOrientation);
  const setSync = useSplitStore((s) => s.setSync);
  const setOpen = useSplitStore((s) => s.setOpen);
  const tabs = useWorkspaceStore((s) => s.tabs);
  const activeId = useWorkspaceStore((s) => s.activeId);

  const engineRef = useRef<PdfJsEngine | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [numPages, setNumPages] = useState(0);
  const [status, setStatus] = useState<'empty' | 'loading' | 'ready' | 'error'>('empty');
  const [error, setError] = useState('');
  const [generation, setGeneration] = useState(0);
  const [scale, setScale] = useState(1);
  const [fitWidth, setFitWidth] = useState(true);
  const [page, setPage] = useState(1);
  const sizes = useRef(new Map<number, Size>());
  const [, setSizeTick] = useState(0);

  if (!engineRef.current) engineRef.current = new PdfJsEngine();
  const engine = engineRef.current;

  // Release the engine's document and worker resources when the pane closes.
  useEffect(() => {
    return () => {
      void engine.closeDocument();
    };
  }, [engine]);

  const tab = source?.kind === 'tab' ? tabs.find((t) => t.id === source.id) : undefined;
  const isActiveDoc = !!tab && tab.id === activeId;

  // (Re)load whenever the chosen source changes.
  const sourceKey =
    source?.kind === 'tab' ? `tab:${source.id}` : source ? `file:${source.name}` : '';
  useEffect(() => {
    let live = true;
    void (async () => {
      if (!source) {
        setStatus('empty');
        setNumPages(0);
        sizes.current.clear();
        void engine.closeDocument();
        return;
      }
      setStatus('loading');
      try {
        let doc: DocumentSource | null = null;
        if (source.kind === 'file') {
          doc = { kind: 'bytes', data: source.data.slice(), name: source.name };
        } else {
          const t = useWorkspaceStore.getState().tabs.find((x) => x.id === source.id);
          if (t) doc = await getTabSource(t, t.id === useWorkspaceStore.getState().activeId);
        }
        if (!doc) throw new Error('That document is no longer open');
        const info = await engine.loadDocument(doc);
        if (!live) return;
        sizes.current = new Map();
        const first = await engine.getPageDimensions(1, 1);
        sizes.current.set(1, { width: first.width, height: first.height });
        setNumPages(info.numPages);
        setPage(1);
        setFitWidth(true);
        setGeneration((g) => g + 1);
        setStatus('ready');
        scrollRef.current?.scrollTo({ top: 0 });
      } catch (e) {
        if (live) {
          setError(e instanceof Error ? e.message : 'Could not open this document');
          setStatus('error');
        }
      }
    })();
    return () => {
      live = false;
    };
    // sourceKey identifies the source; `source` itself changes identity needlessly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey, engine]);

  const onMeasure = useCallback((n: number, size: Size) => {
    sizes.current.set(n, size);
    setSizeTick((t) => t + 1);
  }, []);

  const first = sizes.current.get(1) ?? { width: 612, height: 792 };

  // Fit width: track the pane's own width.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el || !fitWidth) return;
    const apply = () => {
      const w = el.clientWidth - PAD * 2 - 16;
      if (w > 0) setScale(Math.min(MAX_SCALE, Math.max(MIN_SCALE, w / first.width)));
    };
    apply();
    const ro = new ResizeObserver(apply);
    ro.observe(el);
    return () => ro.disconnect();
  }, [fitWidth, first.width, status]);

  // Which page is at the top of the pane.
  const onScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    let current = 1;
    for (const node of el.querySelectorAll<HTMLElement>('[data-split-page]')) {
      if (node.offsetTop - 40 <= el.scrollTop) current = Number(node.dataset.splitPage);
      else break;
    }
    setPage(current);
  }, []);

  // Linked scrolling: both views follow each other by how far down they are.
  useEffect(() => {
    if (!sync || status !== 'ready') return;
    const pane = scrollRef.current;
    const main = getViewerElement();
    if (!pane || !main) return;
    const quiet = { pane: 0, main: 0 };
    const follow = (from: HTMLElement, to: HTMLElement, key: 'pane' | 'main') => () => {
      if (performance.now() < quiet[key === 'pane' ? 'main' : 'pane']) return;
      const top = scrollTopForFraction(to, scrollFraction(from));
      if (Math.abs(top - to.scrollTop) < 2) return;
      quiet[key] = performance.now() + 80;
      to.scrollTo({ top, behavior: 'instant' });
    };
    const onMain = follow(main, pane, 'main');
    const onPane = follow(pane, main, 'pane');
    main.addEventListener('scroll', onMain, { passive: true });
    pane.addEventListener('scroll', onPane, { passive: true });
    return () => {
      main.removeEventListener('scroll', onMain);
      pane.removeEventListener('scroll', onPane);
    };
  }, [sync, status, generation]);

  const goTo = (n: number) => {
    const target = Math.min(numPages, Math.max(1, n));
    const node = scrollRef.current?.querySelector<HTMLElement>(`[data-split-page="${target}"]`);
    if (node) scrollRef.current?.scrollTo({ top: node.offsetTop - 12 });
    setPage(target);
  };
  const zoom = (factor: number) => {
    setFitWidth(false);
    setScale((s) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s * factor)));
  };

  const chooseSource = async (value: string) => {
    if (value === FILE_OPTION) {
      const picked = await pickAndReadDocument();
      if (picked?.kind === 'bytes') {
        setSource({ kind: 'file', name: picked.name ?? 'Document.pdf', data: picked.data });
      }
      return;
    }
    setSource({ kind: 'tab', id: value });
  };
  const selectValue = source?.kind === 'tab' ? source.id : source ? FILE_OPTION : '';
  const hasSource = source !== null;

  return (
    <section className="vnyl-split" aria-label="Second document">
      <div className="vnyl-split__bar" role="toolbar" aria-label="Second document controls">
        <select
          className="vnyl-split__select"
          aria-label="Document shown in the second pane"
          value={selectValue}
          onChange={(e) => { if (e.target.value) void chooseSource(e.target.value); }}
        >
          <option value="" disabled>Choose a document…</option>
          {tabs.map((t) => (
            <option key={t.id} value={t.id}>
              {t.id === activeId ? `${t.name} (this document)` : t.name}
            </option>
          ))}
          {source?.kind === 'file' && <option value={FILE_OPTION}>{source.name}</option>}
          <option value={FILE_OPTION}>Open another file…</option>
        </select>
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Previous page"
          disabled={page <= 1}
          onClick={() => goTo(page - 1)}
        >
          <Icon name="chevron-left" size={16} />
        </button>
        <span className="vnyl-split__page" aria-live="polite">
          {page} / {numPages || '–'}
        </span>
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Next page"
          disabled={page >= numPages}
          onClick={() => goTo(page + 1)}
        >
          <Icon name="chevron-right" size={16} />
        </button>
        <span className="vnyl-split__spacer" />
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Zoom out"
          disabled={!hasSource}
          onClick={() => zoom(1 / 1.2)}
        >
          <Icon name="zoom-out" size={16} />
        </button>
        <span className="vnyl-split__zoom">{Math.round(scale * 100)}%</span>
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Zoom in"
          disabled={!hasSource}
          onClick={() => zoom(1.2)}
        >
          <Icon name="zoom-in" size={16} />
        </button>
        <button
          type="button"
          className={`vnyl-icon-button${fitWidth ? ' is-active' : ''}`}
          aria-label="Fit width"
          aria-pressed={fitWidth}
          disabled={!hasSource}
          onClick={() => setFitWidth(true)}
        >
          <Icon name="fit-width" size={16} />
        </button>
        <button
          type="button"
          className={`vnyl-icon-button${sync ? ' is-active' : ''}`}
          aria-label="Scroll both views together"
          aria-pressed={sync}
          title="Scroll both views together"
          disabled={!hasSource}
          onClick={() => setSync(!sync)}
        >
          <Icon name="link" size={16} />
        </button>
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label={
            orientation === 'columns' ? 'Stack views top and bottom' : 'Place views side by side'
          }
          title={orientation === 'columns' ? 'Stack top and bottom' : 'Side by side'}
          onClick={() => setOrientation(orientation === 'columns' ? 'rows' : 'columns')}
        >
          <Icon name={orientation === 'columns' ? 'split-rows' : 'split-columns'} size={16} />
        </button>
        <button
          type="button"
          className="vnyl-icon-button"
          aria-label="Close split view"
          onClick={() => setOpen(false)}
        >
          <Icon name="x" size={16} />
        </button>
      </div>
      {/* Focusable so keyboard users can scroll it with the arrow and Page keys. */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex */}
      <div ref={scrollRef} className="vnyl-split__scroll" onScroll={onScroll} tabIndex={0}>
        {status === 'empty' && (
          <div className="vnyl-split__empty">
            <Icon name="split-columns" size={24} />
            <strong>Choose a second document</strong>
            <span>Select another open tab or open a PDF for this pane.</span>
            <button type="button" className="vnyl-button vnyl-button--ghost" onClick={() => void chooseSource(FILE_OPTION)}>
              Open a PDF
            </button>
          </div>
        )}
        {status === 'loading' && <p className="vnyl-split__note">Loading…</p>}
        {status === 'error' && <p className="vnyl-split__note">{error}</p>}
        {status === 'ready' && (
          <div className="vnyl-split__pages">
            {Array.from({ length: numPages }, (_, i) => (
              <SplitPage
                key={`${generation}-${i + 1}`}
                engine={engine}
                pageNumber={i + 1}
                scale={scale}
                size={sizes.current.get(i + 1)}
                fallback={first}
                onMeasure={onMeasure}
                showMarkup={isActiveDoc}
                generation={generation}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
