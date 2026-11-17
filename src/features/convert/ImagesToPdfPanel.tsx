import { useEffect, useRef, useState } from 'react';

import { Button, Icon, ModalProgress, Segmented } from '@/components/common';
import { saveBytes } from '@/features/export';
import { loadSource } from '@/state/actions';

import { formatBytes } from './engines';
import { baseNameOf, sanitizeFileName } from './naming';
import { imagesToPdf, type Orientation, type PageSize } from './imagesToPdf';
import { useHubStore } from './store';
import { useJob } from './useJob';
import { ResultBox } from './ResultBox';

const ACCEPT = 'image/png,image/jpeg,image/webp,image/bmp,image/gif';

interface Item {
  id: number;
  file: File;
  /** Object URL for the thumbnail; revoked when the item goes away. */
  url: string;
}

let nextId = 1;

/** Several images into one PDF: pick or drop files, put them in order, choose the pages. */
export function ImagesToPdfPanel() {
  const [items, setItems] = useState<Item[]>([]);
  const [size, setSize] = useState<PageSize>('a4');
  const [orientation, setOrientation] = useState<Orientation>('auto');
  const [margin, setMargin] = useState('18');
  const [name, setName] = useState('images.pdf');
  const [over, setOver] = useState(false);
  const [result, setResult] = useState<{ bytes: Uint8Array; name: string; pages: number } | null>(
    null,
  );
  const job = useJob<Uint8Array>();
  const input = useRef<HTMLInputElement>(null);
  const live = useRef(items);
  live.current = items;

  // Free every thumbnail URL when the panel closes.
  useEffect(() => () => live.current.forEach((i) => URL.revokeObjectURL(i.url)), []);
  useEffect(() => setResult(null), [items, size, orientation, margin, name]);

  const add = (files: FileList | File[]) => {
    const images = Array.from(files).filter((f) => f.type.startsWith('image/'));
    if (images.length === 0) return;
    setItems((cur) => [
      ...cur,
      ...images.map((file) => ({ id: nextId++, file, url: URL.createObjectURL(file) })),
    ]);
  };
  const remove = (id: number) =>
    setItems((cur) => {
      cur.filter((i) => i.id === id).forEach((i) => URL.revokeObjectURL(i.url));
      return cur.filter((i) => i.id !== id);
    });
  const move = (index: number, by: -1 | 1) =>
    setItems((cur) => {
      const to = index + by;
      if (to < 0 || to >= cur.length) return cur;
      const next = [...cur];
      [next[index], next[to]] = [next[to], next[index]];
      return next;
    });
  const sortByName = () =>
    setItems((cur) =>
      [...cur].sort((a, b) => a.file.name.localeCompare(b.file.name, undefined, { numeric: true })),
    );

  const outName = () => {
    const stem = sanitizeFileName(baseNameOf(name.trim() || 'images'));
    return `${stem}.pdf`;
  };

  const create = async () => {
    const bytes = await job.run((signal) =>
      imagesToPdf(
        items.map((i) => i.file),
        { size, orientation, margin: Number(margin) },
        job.setProgress,
        signal,
      ),
    );
    if (bytes) setResult({ bytes, name: outName(), pages: items.length });
  };

  const openResult = async () => {
    if (!result) return;
    useHubStore.getState().close();
    await loadSource({ kind: 'bytes', data: result.bytes.slice(), name: result.name });
  };

  return (
    <div className="vnyl-tool-panel">
      <div
        className={`vnyl-dropzone${over ? ' is-over' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setOver(false);
          add(e.dataTransfer.files);
        }}
      >
        <Icon name="image" size={22} />
        <p>
          {items.length === 0
            ? 'Add the images to turn into a PDF.'
            : `${items.length} image${items.length === 1 ? '' : 's'} added.`}
        </p>
        <Button
          variant={items.length === 0 ? 'primary' : undefined}
          onClick={() => input.current?.click()}
        >
          {items.length === 0 ? 'Choose images…' : 'Add more…'}
        </Button>
        <input
          ref={input}
          type="file"
          accept={ACCEPT}
          multiple
          hidden
          onChange={(e) => {
            if (e.target.files) add(e.target.files);
            e.target.value = '';
          }}
        />
      </div>

      {items.length > 0 && (
        <>
          <div className="vnyl-tool-actions">
            <Button onClick={sortByName}>Sort by name</Button>
            <Button
              onClick={() => {
                items.forEach((i) => URL.revokeObjectURL(i.url));
                setItems([]);
              }}
            >
              Clear all
            </Button>
          </div>
          <ol className="vnyl-image-list" aria-label="Images, in page order">
            {items.map((item, i) => (
              <li key={item.id}>
                <img src={item.url} alt="" className="vnyl-image-list__thumb" />
                <span className="vnyl-image-list__num">{i + 1}</span>
                <span className="vnyl-tool-files__name" title={item.file.name}>
                  {item.file.name}
                </span>
                <span className="vnyl-tool-files__size">{formatBytes(item.file.size)}</span>
                <button
                  type="button"
                  className="vnyl-icon-button"
                  aria-label={`Move ${item.file.name} earlier`}
                  disabled={i === 0}
                  onClick={() => move(i, -1)}
                >
                  <Icon name="chevron-up" size={16} />
                </button>
                <button
                  type="button"
                  className="vnyl-icon-button"
                  aria-label={`Move ${item.file.name} later`}
                  disabled={i === items.length - 1}
                  onClick={() => move(i, 1)}
                >
                  <Icon name="chevron-down" size={16} />
                </button>
                <button
                  type="button"
                  className="vnyl-icon-button"
                  aria-label={`Remove ${item.file.name}`}
                  onClick={() => remove(item.id)}
                >
                  <Icon name="x" size={16} />
                </button>
              </li>
            ))}
          </ol>
        </>
      )}

      <div className="vnyl-tool-field">
        <span>Page size</span>
        <Segmented<PageSize>
          aria-label="Page size"
          value={size}
          onChange={setSize}
          options={[
            { value: 'fit', label: 'Fit to image' },
            { value: 'a4', label: 'A4' },
            { value: 'letter', label: 'Letter' },
          ]}
        />
      </div>
      {size !== 'fit' && (
        <div className="vnyl-tool-field">
          <span>Orientation</span>
          <Segmented<Orientation>
            aria-label="Orientation"
            value={orientation}
            onChange={setOrientation}
            options={[
              { value: 'auto', label: 'Match each image' },
              { value: 'portrait', label: 'Portrait' },
              { value: 'landscape', label: 'Landscape' },
            ]}
          />
        </div>
      )}
      <div className="vnyl-tool-field">
        <span>Margin</span>
        <Segmented
          aria-label="Margin"
          value={margin}
          onChange={setMargin}
          options={[
            { value: '0', label: 'None' },
            { value: '18', label: 'Small' },
            { value: '36', label: 'Medium' },
          ]}
        />
      </div>
      <label className="vnyl-tool-field">
        <span>File name</span>
        <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
      </label>

      {job.progress ? (
        <div className="vnyl-tool-run">
          <ModalProgress
            percent={Math.round((job.progress.done / Math.max(1, job.progress.total)) * 100)}
          />
          <span>
            Image {Math.min(job.progress.done + 1, job.progress.total)} of {job.progress.total}
          </span>
          <Button onClick={job.cancel}>Cancel</Button>
        </div>
      ) : (
        <Button variant="primary" onClick={() => void create()} disabled={items.length === 0}>
          Create PDF
          {items.length > 0 ? ` from ${items.length} image${items.length === 1 ? '' : 's'}` : ''}
        </Button>
      )}
      {job.error && (
        <p className="vnyl-tool-error" role="alert">
          {job.error}
        </p>
      )}
      {result && (
        <ResultBox>
          <p>
            <strong>{result.name} is ready</strong> ({result.pages} page
            {result.pages === 1 ? '' : 's'}, {formatBytes(result.bytes.length)})
          </p>
          <div className="vnyl-tool-actions">
            <Button variant="primary" onClick={() => void openResult()}>
              Open it
            </Button>
            <Button onClick={() => void saveBytes(result.bytes, result.name)}>Save PDF…</Button>
          </div>
        </ResultBox>
      )}
    </div>
  );
}
