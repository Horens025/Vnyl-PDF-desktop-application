import { useEffect, useState } from 'react';

import { Button, ModalProgress, Segmented } from '@/components/common';

import {
  formatBytes,
  IMAGE_FORMATS,
  MAX_IMAGE_SIDE,
  pdfToImages,
  pdfToText,
  type ImageFormat,
  type OutputFile,
  type TextFormat,
} from './engines';
import { applyTemplate, DEFAULT_TEMPLATE } from './naming';
import { useJob } from './useJob';
import { useSourceAndPages } from './useSourceAndPages';
import { useRevealOnMount } from './useReveal';
import { saveAllToFolder, saveOutput } from './saveOutput';
import { createZip } from './zip';
import { isTauri } from '@/core/document/openDocument';

const MAX_LISTED = 40;

export function SourceFields({
  s,
  showPages = true,
}: {
  s: ReturnType<typeof useSourceAndPages>;
  showPages?: boolean;
}) {
  const rangeError = s.range && 'error' in s.range ? s.range.error : null;
  return (
    <>
      <label className="vnyl-tool-field">
        <span>Document</span>
        <select value={s.sourceId ?? ''} onChange={(e) => s.setSourceId(e.target.value)}>
          {s.tabs.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <small aria-live="polite">
          {s.source.status === 'loading' && 'Opening…'}
          {s.source.status === 'ready' &&
            `${s.source.numPages} page${s.source.numPages === 1 ? '' : 's'}`}
          {s.source.status === 'error' && s.source.error}
        </small>
      </label>
      {showPages && (
        <label className="vnyl-tool-field">
          <span>Pages</span>
          <input
            type="text"
            value={s.pageText}
            placeholder="All pages (or e.g. 1-3, 5, 8-)"
            aria-invalid={rangeError ? true : undefined}
            onChange={(e) => s.setPageText(e.target.value)}
          />
          {rangeError && <small className="vnyl-tool-error">{rangeError}</small>}
        </label>
      )}
    </>
  );
}

export function NoDocument() {
  return <p className="vnyl-compare__note">Open a PDF first, then come back to this tool.</p>;
}

export function Results({
  files,
  note,
  zipName,
}: {
  files: OutputFile[];
  note?: string;
  zipName: string;
}) {
  const [zipping, setZipping] = useState(false);
  const ref = useRevealOnMount();
  const desktop = isTauri();
  const total = files.reduce((n, f) => n + f.blob.size, 0);
  const downloadZip = async () => {
    setZipping(true);
    try {
      const entries = await Promise.all(
        files.map(async (f) => ({
          name: f.name,
          data: new Uint8Array(await f.blob.arrayBuffer()),
        })),
      );
      await saveOutput({
        name: zipName,
        blob: new Blob([new Uint8Array(createZip(entries))], { type: 'application/zip' }),
      });
    } finally {
      setZipping(false);
    }
  };
  return (
    <div ref={ref} className="vnyl-tool-results" aria-live="polite">
      <p>
        <strong>
          {files.length} file{files.length === 1 ? '' : 's'} ready
        </strong>{' '}
        ({formatBytes(total)})
      </p>
      {note && <p className="vnyl-compare__note">{note}</p>}
      <ul className="vnyl-tool-files">
        {files.slice(0, MAX_LISTED).map((f) => (
          <li key={f.name}>
            <span className="vnyl-tool-files__name">{f.name}</span>
            <span className="vnyl-tool-files__size">{formatBytes(f.blob.size)}</span>
            <Button onClick={() => void saveOutput(f)}>{desktop ? 'Save…' : 'Download'}</Button>
          </li>
        ))}
        {files.length > MAX_LISTED && <li>…and {files.length - MAX_LISTED} more (in the zip)</li>}
      </ul>
      {files.length > 1 && (
        <div className="vnyl-tool-actions">
          {desktop && (
            <Button variant="primary" onClick={() => void saveAllToFolder(files)}>
              Save all to folder…
            </Button>
          )}
          <Button
            variant={desktop ? undefined : 'primary'}
            onClick={() => void downloadZip()}
            disabled={zipping}
          >
            {zipping ? 'Zipping…' : desktop ? 'Save as .zip…' : 'Download all (.zip)'}
          </Button>
        </div>
      )}
    </div>
  );
}

function TextResult({
  result,
}: {
  result: { file: OutputFile; text: string; emptyPages: number };
}) {
  const ref = useRevealOnMount();
  return (
    <div ref={ref} className="vnyl-tool-results" aria-live="polite">
      {result.emptyPages > 0 && (
        <p className="vnyl-compare__note">
          {result.emptyPages} page{result.emptyPages === 1 ? ' has' : 's have'} no text. If the
          document is a scan, run OCR first.
        </p>
      )}
      {/* Focusable so keyboard users can scroll a long preview. */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex */}
      <pre className="vnyl-tool-preview" tabIndex={0} aria-label="Text preview">
        {result.text.slice(0, 1500)}
        {result.text.length > 1500 ? '\n…' : ''}
      </pre>
      <div className="vnyl-tool-actions">
        <Button variant="primary" onClick={() => void saveOutput(result.file)}>
          {isTauri() ? 'Save' : 'Download'} {result.file.name}
        </Button>
        <Button onClick={() => void navigator.clipboard?.writeText(result.text)}>Copy text</Button>
      </div>
    </div>
  );
}

export function PdfToImagesPanel() {
  const s = useSourceAndPages();
  const job = useJob<{ files: OutputFile[]; limited: number }>();
  const [format, setFormat] = useState<ImageFormat>('png');
  const [dpi, setDpi] = useState(150);
  const [quality, setQuality] = useState(90);
  const [template, setTemplate] = useState(DEFAULT_TEMPLATE);
  const [result, setResult] = useState<{
    files: OutputFile[];
    limited: number;
    zip: string;
  } | null>(null);

  useEffect(() => setResult(null), [s.sourceId, format, dpi, quality, template, s.pageText]);

  if (s.tabs.length === 0) return <NoDocument />;
  const valid = s.range && 'pages' in s.range ? s.range.pages : null;
  const sample = applyTemplate(template, {
    name: s.source.name || 'document',
    page: valid?.[0] ?? 1,
    total: s.source.numPages || 1,
  });
  const lossy = format !== 'png';

  const convert = async () => {
    const engine = s.source.status === 'ready' ? s.source.engineRef.current : null;
    if (!engine || !valid) return;
    const out = await job.run((signal) =>
      pdfToImages(
        engine,
        {
          pages: valid,
          totalPages: s.source.numPages,
          format,
          dpi,
          quality: quality / 100,
          template,
          baseName: s.source.name,
        },
        job.setProgress,
        signal,
      ),
    );
    if (out) setResult({ ...out, zip: `${s.source.name}-images.zip` });
  };

  return (
    <div className="vnyl-tool-panel">
      <SourceFields s={s} />
      <div className="vnyl-tool-field">
        <span>Format</span>
        <Segmented<ImageFormat>
          aria-label="Image format"
          value={format}
          onChange={setFormat}
          options={(Object.keys(IMAGE_FORMATS) as ImageFormat[]).map((f) => ({
            value: f,
            label: IMAGE_FORMATS[f].label,
          }))}
        />
      </div>
      <div className="vnyl-tool-field">
        <span>Resolution</span>
        <Segmented
          aria-label="Resolution"
          value={String(dpi)}
          onChange={(v) => setDpi(Number(v))}
          options={[
            { value: '72', label: '72 · Screen' },
            { value: '150', label: '150 · Standard' },
            { value: '300', label: '300 · Print' },
          ]}
        />
        <small>Pages are limited to {MAX_IMAGE_SIDE} px on the longest side.</small>
      </div>
      {lossy && (
        <label className="vnyl-tool-field">
          <span>Quality: {quality}%</span>
          <input
            type="range"
            min={40}
            max={100}
            step={5}
            value={quality}
            onChange={(e) => setQuality(Number(e.target.value))}
          />
        </label>
      )}
      <label className="vnyl-tool-field">
        <span>File names</span>
        <input type="text" value={template} onChange={(e) => setTemplate(e.target.value)} />
        <small>
          Use {'{name}'}, {'{page}'} and {'{total}'}. Example: {sample}.{IMAGE_FORMATS[format].ext}
        </small>
      </label>

      {job.progress ? (
        <div className="vnyl-tool-run">
          <ModalProgress
            percent={Math.round((job.progress.done / Math.max(1, job.progress.total)) * 100)}
          />
          <span>
            Page {Math.min(job.progress.done + 1, job.progress.total)} of {job.progress.total}
          </span>
          <Button onClick={job.cancel}>Cancel</Button>
        </div>
      ) : (
        <Button
          variant="primary"
          onClick={() => void convert()}
          disabled={!valid || s.source.status !== 'ready'}
        >
          Convert {valid ? `${valid.length} page${valid.length === 1 ? '' : 's'}` : ''}
        </Button>
      )}
      {job.error && (
        <p className="vnyl-tool-error" role="alert">
          {job.error}
        </p>
      )}
      {result && (
        <Results
          files={result.files}
          zipName={result.zip}
          note={
            result.limited > 0
              ? `${result.limited} page${result.limited === 1 ? ' was' : 's were'} reduced to fit ${MAX_IMAGE_SIDE} px.`
              : undefined
          }
        />
      )}
    </div>
  );
}

export function PdfToTextPanel() {
  const s = useSourceAndPages();
  const job = useJob<{ file: OutputFile; text: string; emptyPages: number }>();
  const [format, setFormat] = useState<TextFormat>('txt');
  const [result, setResult] = useState<{
    file: OutputFile;
    text: string;
    emptyPages: number;
  } | null>(null);

  useEffect(() => setResult(null), [s.sourceId, format, s.pageText]);

  if (s.tabs.length === 0) return <NoDocument />;
  const valid = s.range && 'pages' in s.range ? s.range.pages : null;

  const convert = async () => {
    const engine = s.source.status === 'ready' ? s.source.engineRef.current : null;
    if (!engine || !valid) return;
    const out = await job.run((signal) =>
      pdfToText(engine, { pages: valid, format, baseName: s.source.name }, job.setProgress, signal),
    );
    if (out) setResult(out);
  };

  return (
    <div className="vnyl-tool-panel">
      <SourceFields s={s} />
      <div className="vnyl-tool-field">
        <span>Format</span>
        <Segmented<TextFormat>
          aria-label="Text format"
          value={format}
          onChange={setFormat}
          options={[
            { value: 'txt', label: 'Plain text (.txt)' },
            { value: 'md', label: 'Markdown (.md)' },
          ]}
        />
      </div>
      {job.progress ? (
        <div className="vnyl-tool-run">
          <ModalProgress
            percent={Math.round((job.progress.done / Math.max(1, job.progress.total)) * 100)}
          />
          <span>
            Page {Math.min(job.progress.done + 1, job.progress.total)} of {job.progress.total}
          </span>
          <Button onClick={job.cancel}>Cancel</Button>
        </div>
      ) : (
        <Button
          variant="primary"
          onClick={() => void convert()}
          disabled={!valid || s.source.status !== 'ready'}
        >
          Extract text {valid ? `from ${valid.length} page${valid.length === 1 ? '' : 's'}` : ''}
        </Button>
      )}
      {job.error && (
        <p className="vnyl-tool-error" role="alert">
          {job.error}
        </p>
      )}
      {result && <TextResult result={result} />}
    </div>
  );
}
