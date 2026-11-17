import { useEffect, useState } from 'react';

import { Button, ModalProgress, Segmented } from '@/components/common';
import { saveBytes } from '@/features/export';
import { loadSource } from '@/state/actions';

import { formatBytes, type OutputFile } from './engines';
import { baseNameOf, sanitizeFileName } from './naming';
import {
  COMPRESS_PRESETS,
  compressPdf,
  extractPages,
  planSplit,
  splitPdf,
  type CompressPreset,
  type CompressResult,
  type SplitMode,
} from './pdfTools';
import { NoDocument, Results, SourceFields } from './panels';
import { useSourceAndPages } from './useSourceAndPages';
import { ResultBox } from './ResultBox';
import { loadSourceBytes } from './sourceBytes';
import { useHubStore } from './store';
import { useJob } from './useJob';

/** Progress bar + cancel, shared by the three tools. */
function RunBar({
  job,
  noun,
}: {
  job: { progress: { done: number; total: number } | null; cancel: () => void };
  noun: string;
}) {
  if (!job.progress) return null;
  return (
    <div className="vnyl-tool-run">
      <ModalProgress
        percent={Math.round((job.progress.done / Math.max(1, job.progress.total)) * 100)}
      />
      <span>
        {noun} {Math.min(job.progress.done + 1, job.progress.total)} of {job.progress.total}
      </span>
      <Button onClick={job.cancel}>Cancel</Button>
    </div>
  );
}

function ErrorLine({ message }: { message: string }) {
  return message ? (
    <p className="vnyl-tool-error" role="alert">
      {message}
    </p>
  ) : null;
}

/** Open a freshly made PDF as a new tab, closing the hub first. */
async function openAsTab(bytes: Uint8Array, name: string) {
  useHubStore.getState().close();
  await loadSource({ kind: 'bytes', data: bytes.slice(), name });
}

export function SplitPanel() {
  const s = useSourceAndPages();
  const job = useJob<OutputFile[]>();
  const [mode, setMode] = useState<SplitMode>('each');
  const [every, setEvery] = useState('5');
  const [ranges, setRanges] = useState('');
  const [files, setFiles] = useState<OutputFile[] | null>(null);

  useEffect(() => setFiles(null), [s.sourceId, mode, every, ranges]);
  if (s.tabs.length === 0) return <NoDocument />;

  const total = s.source.status === 'ready' ? s.source.numPages : 0;
  const plan = total > 0 ? planSplit(mode, total, Number(every), ranges) : null;
  const planError = plan && 'error' in plan ? plan.error : null;
  const groups = plan && 'groups' in plan ? plan.groups : null;

  const run = async () => {
    if (!s.sourceId || !groups) return;
    const out = await job.run(async (signal) => {
      const bytes = await loadSourceBytes(s.sourceId!);
      return splitPdf(bytes, groups, s.source.name, job.setProgress, signal);
    });
    if (out) setFiles(out);
  };

  return (
    <div className="vnyl-tool-panel">
      <SourceFields s={s} showPages={false} />
      <div className="vnyl-tool-field">
        <span>Split into</span>
        <Segmented<SplitMode>
          aria-label="How to split"
          value={mode}
          onChange={setMode}
          options={[
            { value: 'each', label: 'Single pages' },
            { value: 'every', label: 'Every N pages' },
            { value: 'ranges', label: 'Page ranges' },
          ]}
        />
      </div>
      {mode === 'every' && (
        <label className="vnyl-tool-field">
          <span>Pages per file</span>
          <input
            type="text"
            inputMode="numeric"
            value={every}
            onChange={(e) => setEvery(e.target.value)}
          />
        </label>
      )}
      {mode === 'ranges' && (
        <label className="vnyl-tool-field">
          <span>Ranges</span>
          <input
            type="text"
            value={ranges}
            placeholder="e.g. 1-3, 4-6, 7-"
            aria-invalid={planError ? true : undefined}
            onChange={(e) => setRanges(e.target.value)}
          />
          <small>Each range becomes its own PDF.</small>
        </label>
      )}
      {planError && <p className="vnyl-tool-error">{planError}</p>}
      <RunBar job={job} noun="File" />
      {!job.progress && (
        <Button
          variant="primary"
          onClick={() => void run()}
          disabled={!groups || s.source.status !== 'ready'}
        >
          Split{groups ? ` into ${groups.length} file${groups.length === 1 ? '' : 's'}` : ''}
        </Button>
      )}
      <ErrorLine message={job.error} />
      {files && <Results files={files} zipName={`${s.source.name}-split.zip`} />}
    </div>
  );
}

export function ExtractPanel() {
  const s = useSourceAndPages();
  const job = useJob<Uint8Array>();
  const [name, setName] = useState('');
  const [result, setResult] = useState<{ bytes: Uint8Array; name: string; pages: number } | null>(
    null,
  );

  useEffect(() => setResult(null), [s.sourceId, s.pageText, name]);
  if (s.tabs.length === 0) return <NoDocument />;

  const pages = s.range && 'pages' in s.range ? s.range.pages : null;
  const needsPages = s.pageText.trim() === '' || s.pageText.trim().toLowerCase() === 'all';
  const outName = `${sanitizeFileName(baseNameOf(name.trim() || `${s.source.name}-extract`))}.pdf`;

  const run = async () => {
    if (!s.sourceId || !pages) return;
    const bytes = await job.run(async () =>
      extractPages(await loadSourceBytes(s.sourceId!), pages),
    );
    if (bytes) setResult({ bytes, name: outName, pages: pages.length });
  };

  return (
    <div className="vnyl-tool-panel">
      <SourceFields s={s} />
      <p className="vnyl-compare__note">
        Enter the pages to keep, in the order you want them, for example <code>3, 1, 5-7</code>.
      </p>
      <label className="vnyl-tool-field">
        <span>New file name</span>
        <input
          type="text"
          value={name}
          placeholder={`${s.source.name}-extract`}
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <RunBar job={job} noun="Step" />
      {!job.progress && (
        <Button
          variant="primary"
          onClick={() => void run()}
          disabled={!pages || needsPages || s.source.status !== 'ready'}
        >
          Extract
          {pages && !needsPages ? ` ${pages.length} page${pages.length === 1 ? '' : 's'}` : ''}
        </Button>
      )}
      <ErrorLine message={job.error} />
      {result && (
        <ResultBox>
          <p>
            <strong>{result.name} is ready</strong> ({result.pages} page
            {result.pages === 1 ? '' : 's'}, {formatBytes(result.bytes.length)})
          </p>
          <div className="vnyl-tool-actions">
            <Button variant="primary" onClick={() => void openAsTab(result.bytes, result.name)}>
              Open it
            </Button>
            <Button onClick={() => void saveBytes(result.bytes, result.name)}>Save PDF…</Button>
          </div>
        </ResultBox>
      )}
    </div>
  );
}

export function CompressPanel() {
  const s = useSourceAndPages();
  const job = useJob<CompressResult>();
  const [preset, setPreset] = useState<CompressPreset>('balanced');
  const [result, setResult] = useState<(CompressResult & { name: string }) | null>(null);

  useEffect(() => setResult(null), [s.sourceId, preset]);
  if (s.tabs.length === 0) return <NoDocument />;

  const run = async () => {
    if (!s.sourceId) return;
    const out = await job.run(async (signal) =>
      compressPdf(await loadSourceBytes(s.sourceId!), preset, job.setProgress, signal),
    );
    if (out) setResult({ ...out, name: `${s.source.name}-compressed.pdf` });
  };

  const saved = result ? result.before - result.after : 0;
  return (
    <div className="vnyl-tool-panel">
      <SourceFields s={s} showPages={false} />
      <div className="vnyl-tool-field">
        <span>Quality</span>
        <Segmented<CompressPreset>
          aria-label="Compression level"
          value={preset}
          onChange={setPreset}
          options={(Object.keys(COMPRESS_PRESETS) as CompressPreset[]).map((k) => ({
            value: k,
            label: COMPRESS_PRESETS[k].label,
          }))}
        />
        <small>{COMPRESS_PRESETS[preset].hint}.</small>
      </div>
      <p className="vnyl-compare__note">
        Photos inside the PDF are re-saved smaller. Text, fonts and drawings are not touched, so the
        document stays sharp and searchable.
      </p>
      <RunBar job={job} noun="Image" />
      {!job.progress && (
        <Button variant="primary" onClick={() => void run()} disabled={s.source.status !== 'ready'}>
          Compress
        </Button>
      )}
      <ErrorLine message={job.error} />
      {result && (
        <ResultBox>
          {saved > 0 ? (
            <p>
              <strong>
                {formatBytes(result.before)} → {formatBytes(result.after)}
              </strong>{' '}
              ({Math.round((saved / result.before) * 100)}% smaller, {result.recompressed} photo
              {result.recompressed === 1 ? '' : 's'} recompressed)
            </p>
          ) : (
            <p>
              <strong>This file is already small.</strong> There were no photos to shrink, so
              nothing was changed.
            </p>
          )}
          {saved > 0 && (
            <div className="vnyl-tool-actions">
              <Button variant="primary" onClick={() => void openAsTab(result.bytes, result.name)}>
                Open it
              </Button>
              <Button onClick={() => void saveBytes(result.bytes, result.name)}>Save PDF…</Button>
            </div>
          )}
        </ResultBox>
      )}
    </div>
  );
}
