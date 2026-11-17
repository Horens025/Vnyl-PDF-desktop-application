import { useEffect, useMemo, useRef, useState } from 'react';

import { Button, Icon, Modal, Segmented } from '@/components/common';
import { PdfJsEngine } from '@/core/pdf/PdfJsEngine';
import { getTabSource, useWorkspaceStore } from '@/features/workspace';

import { diffPixels, diffStats, diffWords, type DiffOp } from './diff';
import { fitPixels, renderPixels } from './pixels';
import { useCompareStore } from './store';

/** A page differs if its words differ or enough of its pixels do (anti-aliasing noise sits well below this). */
const PIXEL_NOISE = 0.0003;
const SCAN_SCALE = 0.5;
const VIEW_SCALE = 1.2;

interface PageResult {
  page: number;
  added: number;
  removed: number;
  ratio: number;
}

const isChanged = (r: PageResult) => r.added + r.removed > 0 || r.ratio > PIXEL_NOISE;
const tick = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

/**
 * Compare two open documents: a visual diff (ink only in A is red, only in B is
 * green) and a word diff of the extracted text, page by page. Each side loads
 * into its own engine, so neither disturbs the document on screen.
 */
export function CompareModal() {
  const open = useCompareStore((s) => s.open);
  const idA = useCompareStore((s) => s.idA);
  const idB = useCompareStore((s) => s.idB);
  const mode = useCompareStore((s) => s.mode);
  const setIds = useCompareStore((s) => s.setIds);
  const setMode = useCompareStore((s) => s.setMode);
  const close = useCompareStore((s) => s.close);
  const tabs = useWorkspaceStore((s) => s.tabs);

  const engines = useRef<{ a: PdfJsEngine; b: PdfJsEngine } | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [error, setError] = useState('');
  const [counts, setCounts] = useState({ a: 0, b: 0 });
  const [results, setResults] = useState<PageResult[]>([]);
  const [scanned, setScanned] = useState(0);
  const [page, setPage] = useState(1);
  const [ops, setOps] = useState<DiffOp[] | null>(null);
  const [viewRatio, setViewRatio] = useState<number | null>(null);

  const common = Math.min(counts.a, counts.b);
  const pageTotal = Math.max(counts.a, counts.b);

  // Load both documents whenever the dialog opens or a side is changed.
  useEffect(() => {
    if (!open) return;
    const { tabs: all, activeId } = useWorkspaceStore.getState();
    const a = all.find((t) => t.id === idA);
    const b = all.find((t) => t.id === idB);
    if (!a || !b) {
      setStatus('error');
      setError('Open two documents to compare them.');
      return;
    }
    if (!engines.current) engines.current = { a: new PdfJsEngine(), b: new PdfJsEngine() };
    const { a: engA, b: engB } = engines.current;
    let live = true;
    setStatus('loading');
    setResults([]);
    setScanned(0);
    setPage(1);
    void (async () => {
      try {
        const [srcA, srcB] = await Promise.all([
          getTabSource(a, a.id === activeId),
          getTabSource(b, b.id === activeId),
        ]);
        if (!srcA || !srcB) throw new Error('One of the documents can no longer be read');
        const [infoA, infoB] = await Promise.all([
          engA.loadDocument(srcA),
          engB.loadDocument(srcB),
        ]);
        if (!live) return;
        setCounts({ a: infoA.numPages, b: infoB.numPages });
        setStatus('ready');
      } catch (e) {
        if (live) {
          setError(e instanceof Error ? e.message : 'Could not open the documents');
          setStatus('error');
        }
      }
    })();
    return () => {
      live = false;
    };
  }, [open, idA, idB]);

  // Release both engines when the dialog closes.
  useEffect(() => {
    if (open) return;
    const e = engines.current;
    engines.current = null;
    if (e) void Promise.all([e.a.closeDocument(), e.b.closeDocument()]);
    setStatus('idle');
  }, [open]);

  // Scan every shared page in the background to find which ones changed.
  useEffect(() => {
    if (status !== 'ready' || !engines.current || common === 0) return;
    const { a, b } = engines.current;
    let cancelled = false;
    void (async () => {
      const acc: PageResult[] = [];
      for (let p = 1; p <= common; p++) {
        if (cancelled) return;
        try {
          const [ta, tb] = await Promise.all([a.getPageText(p), b.getPageText(p)]);
          const stats = diffStats(diffWords(ta, tb));
          const [pa, pb] = await Promise.all([
            renderPixels(a, p, SCAN_SCALE),
            renderPixels(b, p, SCAN_SCALE),
          ]);
          const ratio = diffPixels(pa, fitPixels(pb, pa.width, pa.height)).ratio;
          acc.push({ page: p, ...stats, ratio });
        } catch {
          acc.push({ page: p, added: 0, removed: 0, ratio: 0 });
        }
        if (cancelled) return;
        setResults([...acc]);
        setScanned(p);
        await tick();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [status, common]);

  // Draw / compute the selected page's diff.
  useEffect(() => {
    if (status !== 'ready' || !engines.current || page > common) {
      setOps(null);
      setViewRatio(null);
      return;
    }
    const { a, b } = engines.current;
    const controller = new AbortController();
    let live = true;
    void (async () => {
      try {
        if (mode === 'text') {
          const [ta, tb] = await Promise.all([a.getPageText(page), b.getPageText(page)]);
          if (live) setOps(diffWords(ta, tb));
        } else {
          const [pa, pb] = await Promise.all([
            renderPixels(a, page, VIEW_SCALE, controller.signal),
            renderPixels(b, page, VIEW_SCALE, controller.signal),
          ]);
          if (!live) return;
          const d = diffPixels(pa, fitPixels(pb, pa.width, pa.height));
          const canvas = canvasRef.current;
          if (canvas) {
            canvas.width = pa.width;
            canvas.height = pa.height;
            const ctx = canvas.getContext('2d');
            if (ctx) {
              const image = ctx.createImageData(pa.width, pa.height);
              image.data.set(d.data);
              ctx.putImageData(image, 0, 0);
            }
          }
          setViewRatio(d.ratio);
        }
      } catch {
        /* aborted by changing page or mode */
      }
    })();
    return () => {
      live = false;
      controller.abort();
    };
  }, [status, page, mode, common]);

  const byPage = useMemo(() => new Map(results.map((r) => [r.page, r])), [results]);
  const changedPages = results.filter(isChanged).map((r) => r.page);
  const goDiff = (step: 1 | -1) => {
    const candidates = changedPages.filter((p) => (step === 1 ? p > page : p < page));
    const next = step === 1 ? candidates[0] : candidates[candidates.length - 1];
    if (next) setPage(next);
  };
  const stats = ops ? diffStats(ops) : null;
  const nameOf = (id: string | null) => tabs.find((t) => t.id === id)?.name ?? '';
  const scanning = status === 'ready' && scanned < common;

  const picker = (label: string, value: string | null, onChange: (id: string) => void) => (
    <label className="vnyl-compare__pick">
      <span>{label}</span>
      <select value={value ?? ''} onChange={(e) => onChange(e.target.value)}>
        {tabs.map((t) => (
          <option key={t.id} value={t.id}>
            {t.name}
          </option>
        ))}
      </select>
    </label>
  );

  let summary = '';
  if (status === 'ready') {
    if (page > common) {
      summary = `Page ${page} exists only in ${counts.a > counts.b ? 'A' : 'B'}.`;
    } else if (mode === 'text' && stats) {
      summary =
        stats.added + stats.removed === 0
          ? `Page ${page}: the text is identical.`
          : `Page ${page}: ${stats.added} word${stats.added === 1 ? '' : 's'} added, ${stats.removed} removed.`;
    } else if (mode === 'visual' && viewRatio !== null) {
      summary =
        viewRatio <= PIXEL_NOISE
          ? `Page ${page}: no visible differences.`
          : `Page ${page}: ${(viewRatio * 100).toFixed(2)}% of the page differs.`;
    }
  }

  return (
    <Modal open={open} title="Compare documents" onDismiss={close} size="wide">
      <div className="vnyl-modal__body vnyl-compare">
        <div className="vnyl-compare__controls">
          {picker('A (original)', idA, (id) => setIds(id, idB))}
          <button
            type="button"
            className="vnyl-icon-button"
            aria-label="Swap A and B"
            title="Swap A and B"
            onClick={() => setIds(idB, idA)}
          >
            <Icon name="split-columns" size={16} />
          </button>
          {picker('B (revised)', idB, (id) => setIds(idA, id))}
          <Segmented
            aria-label="Comparison type"
            value={mode}
            onChange={setMode}
            options={[
              { value: 'visual', label: 'Visual' },
              { value: 'text', label: 'Text' },
            ]}
          />
        </div>

        {status === 'loading' && <p className="vnyl-compare__note">Opening both documents…</p>}
        {status === 'error' && <p className="vnyl-compare__note">{error}</p>}

        {status === 'ready' && (
          <>
            {counts.a !== counts.b && (
              <p className="vnyl-compare__note">
                {nameOf(idA)} has {counts.a} page{counts.a === 1 ? '' : 's'}; {nameOf(idB)} has{' '}
                {counts.b}. Extra pages are marked as differences.
              </p>
            )}
            <div className="vnyl-compare__pages" role="group" aria-label="Pages">
              {Array.from({ length: pageTotal }, (_, i) => i + 1).map((p) => {
                const r = byPage.get(p);
                const extra = p > common;
                const changed = extra || (r ? isChanged(r) : false);
                const pending = !extra && !r;
                return (
                  <button
                    key={p}
                    type="button"
                    className={`vnyl-compare__chip${p === page ? ' is-active' : ''}${changed ? ' is-changed' : ''}`}
                    aria-current={p === page}
                    aria-label={`Page ${p}${changed ? ', has differences' : pending ? ', checking' : ', no differences'}`}
                    onClick={() => setPage(p)}
                  >
                    {p}
                  </button>
                );
              })}
            </div>
            {scanning && (
              <p className="vnyl-compare__note" aria-live="off">
                Checking pages… {scanned} of {common}
              </p>
            )}

            <div className="vnyl-compare__stage">
              {page > common ? (
                <p className="vnyl-compare__note">
                  This page has no counterpart in the other document.
                </p>
              ) : mode === 'visual' ? (
                <canvas
                  ref={canvasRef}
                  className="vnyl-compare__canvas"
                  role="img"
                  aria-label={summary || 'Visual difference of the selected page'}
                />
              ) : (
                // Focusable so keyboard users can scroll a long page of text.
                // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
                <div className="vnyl-compare__text" tabIndex={0}>
                  {ops === null ? (
                    <p className="vnyl-compare__note">Reading text…</p>
                  ) : ops.length === 0 ? (
                    <p className="vnyl-compare__note">No text on this page in either document.</p>
                  ) : (
                    ops.map((op, i) =>
                      op.type === 'equal' ? (
                        <span key={i}>{op.text} </span>
                      ) : op.type === 'add' ? (
                        <ins key={i} className="vnyl-diff-add">
                          {op.text}{' '}
                        </ins>
                      ) : (
                        <del key={i} className="vnyl-diff-del">
                          {op.text}{' '}
                        </del>
                      ),
                    )
                  )}
                </div>
              )}
            </div>

            <p className="vnyl-compare__summary" aria-live="polite">
              <span className="vnyl-compare__legend vnyl-compare__legend--a">Only in A</span>
              <span className="vnyl-compare__legend vnyl-compare__legend--b">Only in B</span>
              {summary}
            </p>
          </>
        )}
      </div>

      <div className="vnyl-modal__footer">
        <Button onClick={() => goDiff(-1)} disabled={!changedPages.some((p) => p < page)}>
          Previous difference
        </Button>
        <Button onClick={() => goDiff(1)} disabled={!changedPages.some((p) => p > page)}>
          Next difference
        </Button>
        <Button variant="primary" onClick={close}>
          Done
        </Button>
      </div>
    </Modal>
  );
}
