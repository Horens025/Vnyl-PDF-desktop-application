import type { PdfDocumentInfo } from '@/core/pdf';

/**
 * Lifecycle hooks for the single active document. They let a feature (the tab
 * workspace) follow loads and closes without state/actions.ts importing it,
 * which would create a circular dependency (the feature calls loadSource).
 */
export interface LoadedEvent {
  info: PdfDocumentInfo;
  /** A private copy of the bytes (pdf.js detaches the original); null for URL sources. */
  bytes: Uint8Array | null;
  url: string | null;
  path: string | null;
}

type BeforeLoad = () => Promise<void> | void;
type Loaded = (event: LoadedEvent) => void;
type Closed = () => void;

const beforeLoad = new Set<BeforeLoad>();
const loaded = new Set<Loaded>();
const closed = new Set<Closed>();

const add = <T>(set: Set<T>, fn: T) => {
  set.add(fn);
  return () => void set.delete(fn);
};

/** Runs before the engine swaps documents, while the outgoing one is still intact. */
export const onBeforeDocumentLoad = (fn: BeforeLoad) => add(beforeLoad, fn);
export const onDocumentLoaded = (fn: Loaded) => add(loaded, fn);
export const onDocumentClosed = (fn: Closed) => add(closed, fn);

export async function emitBeforeDocumentLoad(): Promise<void> {
  for (const fn of [...beforeLoad]) {
    try {
      await fn();
    } catch {
      /* a hook failing must never stop a document from opening */
    }
  }
}
export function emitDocumentLoaded(event: LoadedEvent): void {
  for (const fn of [...loaded]) fn(event);
}
export function emitDocumentClosed(): void {
  for (const fn of [...closed]) fn();
}
